package proxy

import (
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"antigravity-mobile/internal/inspector"
	"github.com/gorilla/websocket"
)

func TestStreamingPayloadContentChanges(t *testing.T) {
	cases := []struct {
		name        string
		index       int
		replacement string
	}{
		{"last_message_append", 1, "tail grows"},
		{"last_message_same_length_correction", 1, "TAIL"},
		{"earlier_message_append", 0, "first grows"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			payload := StreamUpdatePayload{Status: "CASCADE_RUN_STATUS_RUNNING", TotalMessages: 2,
				Messages: []CascadeMessageItem{{ID: "a", Text: "first"}, {ID: "b", Text: "tail"}}}
			before := payload.Fingerprint()
			payload.Messages[tc.index].Text = tc.replacement
			if before == payload.Fingerprint() {
				t.Fatal("changed visible text was suppressed by payload fingerprint")
			}
		})
	}
}

func TestStreamingRawContentChanges(t *testing.T) {
	cases := []struct {
		name        string
		index       int
		replacement string
	}{
		{"last_step_append", 1, "tail grows"},
		{"last_step_same_length_correction", 1, "TAIL"},
		{"earlier_step_append", 0, "first grows"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var raw upstreamTrajectoryResp
			raw.Status = "CASCADE_RUN_STATUS_RUNNING"
			raw.Trajectory.Steps = []TrajectoryStep{
				{Type: "CORTEX_STEP_TYPE_PLANNER_RESPONSE", Status: "CORTEX_STEP_STATUS_RUNNING"},
				{Type: "CORTEX_STEP_TYPE_PLANNER_RESPONSE", Status: "CORTEX_STEP_STATUS_RUNNING"},
			}
			for index, text := range []string{"first", "tail"} {
				raw.Trajectory.Steps[index].PlannerResponse = &struct {
					Response string `json:"response"`
					Thinking string `json:"thinking"`
				}{Response: text}
			}
			before := rawTrajectorySignature(&raw)
			raw.Trajectory.Steps[tc.index].PlannerResponse.Response = tc.replacement
			if before == rawTrajectorySignature(&raw) {
				t.Fatal("changed step text was suppressed by raw trajectory signature")
			}
		})
	}
}

func TestStreamPollingDeliversCorrectionsAndIndependentQueueChanges(t *testing.T) {
	for _, format := range []string{"messages", "steps"} {
		t.Run(format, func(t *testing.T) {
			id := "stream-content-" + format
			var body atomic.Value
			publish := func(first, last, status string) {
				data, err := json.Marshal(map[string]any{"status": status, "trajectory": map[string]any{
					"cascadeId": id, "summary": "Streaming fixture", "steps": []any{
						map[string]any{"type": "CORTEX_STEP_TYPE_PLANNER_RESPONSE", "plannerResponse": map[string]string{"response": first}},
						map[string]any{"type": "CORTEX_STEP_TYPE_PLANNER_RESPONSE", "plannerResponse": map[string]string{"response": last}},
					},
				}})
				if err != nil {
					t.Fatal(err)
				}
				body.Store(data)
			}
			publish("first", "tail", "CASCADE_RUN_STATUS_RUNNING")
			upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if strings.HasSuffix(r.URL.Path, "/GetCascadeTrajectory") {
					w.Write(body.Load().([]byte))
				} else {
					http.NotFound(w, r)
				}
			}))
			defer upstream.Close()
			p := NewProxy(inspector.NewInspector(time.Hour))
			p.updateUpstream(inspector.InstanceInfo{Port: upstream.Listener.Addr().(*net.TCPAddr).Port, IsHealthy: true})
			server := httptest.NewServer(http.HandlerFunc(p.HandleCascadeStream))
			defer server.Close()
			url := "ws" + strings.TrimPrefix(server.URL, "http") + "?cascadeId=" + id + "&format=" + format
			conn, _, err := websocket.DefaultDialer.Dial(url, nil)
			if err != nil {
				t.Fatal(err)
			}
			defer conn.Close()
			updates := make(chan StreamUpdatePayload, 16)
			go func() {
				defer close(updates)
				for {
					var payload StreamUpdatePayload
					if conn.ReadJSON(&payload) != nil {
						return
					}
					updates <- payload
				}
			}()
			next := func(first, last, status string) StreamUpdatePayload {
				t.Helper()
				select {
				case got, ok := <-updates:
					if !ok || len(got.Messages) != 2 || got.Messages[0].Text != first || got.Messages[1].Text != last || got.Status != status {
						t.Fatalf("unexpected snapshot: %+v", got)
					}
					if (len(got.Steps) > 0) != (format == "steps") {
						t.Fatalf("wrong steps for format %s", format)
					}
					return got
				case <-time.After(3 * time.Second):
					t.Fatal("changed content was not delivered")
				}
				return StreamUpdatePayload{}
			}
			if next("first", "tail", "CASCADE_RUN_STATUS_RUNNING").Type != "init" {
				t.Fatal("missing init")
			}
			select {
			case got := <-updates:
				t.Fatalf("unchanged snapshot resent: %+v", got)
			case <-time.After(600 * time.Millisecond):
			}
			publish("first", "TAIL", "CASCADE_RUN_STATUS_RUNNING")
			next("first", "TAIL", "CASCADE_RUN_STATUS_RUNNING")
			publish("first grows", "TAIL", "CASCADE_RUN_STATUS_RUNNING")
			next("first grows", "TAIL", "CASCADE_RUN_STATUS_RUNNING")
			publish("first grows", "TAIL appended", "CASCADE_RUN_STATUS_RUNNING")
			next("first grows", "TAIL appended", "CASCADE_RUN_STATUS_RUNNING")
			pendingCacheMu.Lock()
			pendingCache[id] = &pendingMessagesCacheEntry{fetchedAt: time.Now(), messages: []QueuedMessageItem{{ID: "q", Text: "queued"}}}
			pendingCacheMu.Unlock()
			defer ClearPendingMessagesCache(id)
			if len(next("first grows", "TAIL appended", "CASCADE_RUN_STATUS_RUNNING").QueuedMessages) != 1 {
				t.Fatal("independent queue update lost")
			}
			publish("first grows", "TAIL appended", "CASCADE_RUN_STATUS_IDLE")
			next("first grows", "TAIL appended", "CASCADE_RUN_STATUS_IDLE")
			reconnected, _, err := websocket.DefaultDialer.Dial(url, nil)
			if err != nil {
				t.Fatal(err)
			}
			defer reconnected.Close()
			reconnected.SetReadDeadline(time.Now().Add(3 * time.Second))
			var snapshot StreamUpdatePayload
			if err := reconnected.ReadJSON(&snapshot); err != nil {
				t.Fatal(err)
			}
			if snapshot.Type != "init" || len(snapshot.Messages) != 2 || snapshot.Messages[1].Text != "TAIL appended" {
				t.Fatalf("bad reconnect: %+v", snapshot)
			}
		})
	}
}

func TestStreamFingerprintIgnoresOnlyTransportAndElapsedTime(t *testing.T) {
	p := StreamUpdatePayload{Type: "init", Duration: "1秒", Status: "RUNNING"}
	before := p.Fingerprint()
	p.Type, p.Duration = "update", "2秒"
	if p.Fingerprint() != before {
		t.Fatal("transport metadata alone should not send a duplicate")
	}
	for _, change := range []func(*StreamUpdatePayload){
		func(p *StreamUpdatePayload) { p.QueuedMessages = []QueuedMessageItem{{ID: "q", Text: "changed"}} },
		func(p *StreamUpdatePayload) { p.RunningTasks = []RunningTaskItem{{ID: "task"}} },
		func(p *StreamUpdatePayload) {
			p.PendingInteraction = &PendingInteraction{Type: "permission", StepIndex: 1}
		},
		func(p *StreamUpdatePayload) { p.ProceedArtifactURI = "file:///plan.md" },
	} {
		copy := p
		change(&copy)
		if copy.Fingerprint() == before {
			t.Fatal("visible state change ignored")
		}
	}
}

func BenchmarkStreamContentFingerprint(b *testing.B) {
	for _, size := range []int{2_000, 10_000, 50_000} {
		b.Run(fmt.Sprint(size), func(b *testing.B) {
			payload := StreamUpdatePayload{Status: "RUNNING", Messages: []CascadeMessageItem{{ID: "live", Text: strings.Repeat("字", size)}}}
			b.ReportAllocs()
			for b.Loop() {
				payload.Fingerprint()
			}
		})
	}
}
