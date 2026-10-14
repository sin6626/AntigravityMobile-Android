package proxy

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// StreamUpdatePayload represents a real-time event pushed over WebSocket.
type StreamUpdatePayload struct {
	Type               string               `json:"type"` // "init", "update", "error"
	CascadeID          string               `json:"cascadeId"`
	Title              string               `json:"title,omitempty"`
	Status             string               `json:"status"`
	HasError           bool                 `json:"hasError"`
	ErrorMessage       string               `json:"errorMessage,omitempty"`
	Duration           string               `json:"duration"`
	TotalSteps         int                  `json:"totalSteps"`
	TotalTools         int                  `json:"totalTools"`
	TotalMessages      int                  `json:"totalMessages"`
	HasMore            bool                 `json:"hasMore"`
	NextOffset         int                  `json:"nextOffset"`
	WorkspaceURI       string               `json:"workspaceUri"`
	Steps              []TrajectoryStep     `json:"steps,omitempty"`
	Messages           []CascadeMessageItem `json:"messages,omitempty"`
	QueuedMessages     []QueuedMessageItem  `json:"queuedMessages"`
	RunningTasks       []RunningTaskItem    `json:"runningTasks"`
	IsFullSnapshot     bool                 `json:"isFullSnapshot"`
	CascadeConfigRaw   string               `json:"cascadeConfigRaw,omitempty"`
	CanProceed         bool                 `json:"canProceed"`
	ProceedArtifactURI string               `json:"proceedArtifactUri,omitempty"`
	PendingInteraction *PendingInteraction  `json:"pendingInteraction,omitempty"`
	ActiveModel        string               `json:"activeModel,omitempty"`
	ModelDisplayName   string               `json:"modelDisplayName,omitempty"`
}

// Fingerprint compares visible state, excluding transport type and elapsed time alone.
func (p *StreamUpdatePayload) Fingerprint() string {
	state := *p
	state.Type = ""
	state.Duration = ""
	return streamContentSignature(state)
}

// Compare all steps: earlier updates and equal-length corrections are also changes.
func rawTrajectorySignature(raw *upstreamTrajectoryResp) string {
	if raw == nil {
		return ""
	}
	return streamContentSignature(raw)
}

func streamContentSignature(value any) string {
	data, err := json.Marshal(value)
	if err != nil {
		// An unavailable signature must never suppress an update.
		log.Printf("[Stream] Cannot fingerprint state: %v", err)
		return ""
	}
	return fmt.Sprintf("%x", sha256.Sum256(data))
}

// HandleCascadeStream serves a WebSocket connection for continuous real-time trajectory updates.
func (p *Proxy) HandleCascadeStream(w http.ResponseWriter, r *http.Request) {
	cascadeID := r.URL.Query().Get("cascadeId")
	if cascadeID == "" {
		http.Error(w, "missing cascadeId", http.StatusBadRequest)
		return
	}

	format := r.URL.Query().Get("format")
	clientType := r.URL.Query().Get("client")
	ua := r.UserAgent()
	isMessagesOnly := format == "messages" || clientType == "ios" ||
		((strings.Contains(ua, "CFNetwork") || strings.Contains(ua, "Darwin") || strings.Contains(ua, "Antigravity")) && !strings.Contains(ua, "Mozilla"))

	sanitizeWebSocketHeaders(r)

	clientConn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[Stream] WS upgrade failed: %v", err)
		return
	}
	defer clientConn.Close()

	if verboseRPC {
		log.Printf("[Stream] WS client connected: cascadeId=%s remote=%s client=%s format=%s", cascadeID, r.RemoteAddr, clientType, format)
		defer log.Printf("[Stream] WS client disconnected: cascadeId=%s remote=%s", cascadeID, r.RemoteAddr)
	}

	cur := p.insp.Current()
	var curPort int
	var curToken string
	if cur != nil {
		curPort = cur.Port
		curToken = cur.CSRFToken
	}
	streamTitle := p.lookupCascadeTitle(cascadeID, curPort, curToken)
	if streamTitle == "" {
		streamTitle = "当前会话"
	}
	p.SetActiveStream(cascadeID, streamTitle)
	defer p.ClearActiveStream(cascadeID)

	touchCh, cleanupTouch := p.registerStreamTouchListener(cascadeID)
	defer cleanupTouch()

	var writeMu sync.Mutex
	writeJSON := func(v interface{}) error {
		writeMu.Lock()
		defer writeMu.Unlock()
		clientConn.SetWriteDeadline(time.Now().Add(5 * time.Second))
		return clientConn.WriteJSON(v)
	}

	closeCh := make(chan struct{})
	// Set initial read deadline and register pong handler to keep connection alive
	clientConn.SetReadDeadline(time.Now().Add(45 * time.Second))
	clientConn.SetPongHandler(func(appData string) error {
		clientConn.SetReadDeadline(time.Now().Add(45 * time.Second))
		return nil
	})
	go func() {
		defer close(closeCh)
		for {
			_, _, err := clientConn.ReadMessage()
			if err != nil {
				return
			}
			// Reset read deadline on any client message
			clientConn.SetReadDeadline(time.Now().Add(45 * time.Second))
		}
	}()

	// P7: do the first fetch synchronously before entering the select loop to
	// eliminate the 50ms latency on initial stream push.
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()

	pingTicker := time.NewTicker(20 * time.Second)
	defer pingTicker.Stop()

	lastFingerprint := ""
	lastRawSig := ""
	firstPush := true
	cachedStreamTitle := streamTitle
	lastTitleLookupTime := time.Time{}
	var lastDetails TrajectoryDetails

	// fetchAndSend does one poll + send cycle. Returns false if the connection should close.
	fetchAndSend := func() bool {
		p.mu.RLock()
		port := p.activePort
		token := p.activeToken
		p.mu.RUnlock()

		if port == 0 {
			ticker.Reset(1000 * time.Millisecond)
			return true
		}

		maxAge := 200 * time.Millisecond
		rawResp, err := p.fetchUpstreamTrajectoryWithMaxAge(cascadeID, port, token, maxAge)
		if err != nil {
			ticker.Reset(1000 * time.Millisecond)
			return true
		}

		rawSig := rawTrajectorySignature(rawResp)
		if firstPush || rawSig == "" || rawSig != lastRawSig {
			lastDetails = p.ParseTrajectoryDetails(rawResp)
			lastRawSig = rawSig
		}
		// Pending messages have their own source/cache and can change without trajectory changes.
		details := lastDetails
		if details.Title != "" && details.Title != "未命名会话" {
			cachedStreamTitle = details.Title
		} else if cachedStreamTitle != "" && cachedStreamTitle != "未命名会话" && cachedStreamTitle != "当前会话" {
			details.Title = cachedStreamTitle
		} else if firstPush || time.Since(lastTitleLookupTime) >= 2*time.Second {
			lastTitleLookupTime = time.Now()
			if t := p.lookupCascadeTitle(cascadeID, port, token); t != "" {
				details.Title = t
				cachedStreamTitle = t
			}
		}
		if qm := p.GetCachedOrFetchPendingMessages(cascadeID, port, token); qm != nil {
			details.QueuedMessages = qm
		} else if details.QueuedMessages == nil {
			details.QueuedMessages = []QueuedMessageItem{}
		}
		details.QueuedMessages = p.FilterQueuedMessagesAgainstTrajectory(cascadeID, details.QueuedMessages, rawResp.Trajectory.Steps, details.AllMessages)
		lastDetails = details

		if sink := p.NotificationSink(); sink != nil {
			sink.OnTrajectoryUpdate(&details)
		}
		totalMsgs := len(details.AllMessages)
		streamLimit := 15
		streamStart := totalMsgs - streamLimit
		if streamStart < 0 {
			streamStart = 0
		}
		slicedMessages := details.AllMessages[streamStart:totalMsgs]
		hasMore := streamStart > 0

		payload := StreamUpdatePayload{
			Type:               "update",
			CascadeID:          details.CascadeID,
			Title:              details.Title,
			Status:             details.Status,
			HasError:           details.HasError,
			ErrorMessage:       details.ErrorMessage,
			Duration:           details.Duration,
			TotalSteps:         details.TotalSteps,
			TotalTools:         details.TotalTools,
			TotalMessages:      totalMsgs,
			HasMore:            hasMore,
			NextOffset:         streamStart,
			WorkspaceURI:       details.WorkspaceURI,
			Messages:           slicedMessages,
			QueuedMessages:     details.QueuedMessages,
			RunningTasks:       details.RunningTasks,
			IsFullSnapshot:     !hasMore,
			CascadeConfigRaw:   details.CascadeConfigRaw,
			CanProceed:         details.CanProceed,
			ProceedArtifactURI: details.ProceedArtifactURI,
			PendingInteraction: details.PendingInteraction,
			ActiveModel:        details.ActiveModel,
			ModelDisplayName:   details.ModelDisplayName,
		}

		if !isMessagesOnly {
			payload.Steps = details.Steps
		}

		if firstPush {
			payload.Type = "init"
		}

		fp := payload.Fingerprint()
		if firstPush || fp == "" || fp != lastFingerprint {
			lastFingerprint = fp
			firstPush = false
			if err := writeJSON(payload); err != nil {
				return false
			}
		}

		// Adjust poll interval dynamically: fast when executing or queued messages exist, slower when idle
		if details.Status == "CASCADE_RUN_STATUS_RUNNING" || len(details.QueuedMessages) > 0 {
			ticker.Reset(250 * time.Millisecond)
		} else {
			ticker.Reset(1200 * time.Millisecond)
		}
		return true
	}

	// Immediate first push — no ticker wait needed.
	if !fetchAndSend() {
		return
	}

	for {
		select {
		case <-closeCh:
			return
		case <-touchCh:
			// Instant wake-up upon external touch/message injection without waiting for ticker!
			lastRawSig = ""
			ticker.Reset(10 * time.Millisecond)
		case <-pingTicker.C:
			writeMu.Lock()
			clientConn.SetWriteDeadline(time.Now().Add(5 * time.Second))
			_ = clientConn.WriteMessage(websocket.PingMessage, []byte{})
			writeMu.Unlock()
		case <-ticker.C:
			if !fetchAndSend() {
				return
			}
		}
	}
}
