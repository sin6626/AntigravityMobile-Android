package proxy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"path/filepath"
	"strings"
)

type CascadeDiffFile struct {
	FileURI               string `json:"fileUri"`
	FileName              string `json:"fileName"`
	ActionType            string `json:"actionType"`
	OriginalContent       string `json:"originalContent"`
	ModifiedContent       string `json:"modifiedContent"`
	FirstTouchedStepIndex *int   `json:"firstTouchedStepIndex,omitempty"`
	LastTouchedStepIndex  *int   `json:"lastTouchedStepIndex,omitempty"`
}

type CascadeDiffsResponse struct {
	CascadeID       string            `json:"cascadeId"`
	Files           []CascadeDiffFile `json:"files"`
	UnavailableURIs []string          `json:"unavailableUris,omitempty"`
}

func diffFileName(raw string) string {
	if parsed, err := url.Parse(raw); err == nil && parsed.Path != "" {
		if name := filepath.Base(parsed.Path); name != "." && name != string(filepath.Separator) {
			return name
		}
	}
	return filepath.Base(strings.TrimSpace(raw))
}

// HandleCascadeDiffs returns the exact original and modified contents used by desktop Review Changes.
func (p *Proxy) HandleCascadeDiffs(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	cascadeID := strings.TrimSpace(r.URL.Query().Get("cascadeId"))
	if !cascadeIDRe.MatchString(cascadeID) {
		http.Error(w, `{"error":"invalid cascadeId"}`, http.StatusBadRequest)
		return
	}
	if IsDeletedCascade(cascadeID) {
		http.Error(w, `{"error":"cascade trajectory has been deleted"}`, http.StatusNotFound)
		return
	}
	port, token := p.ActiveUpstream()
	if port == 0 {
		http.Error(w, `{"error":"Antigravity upstream not connected"}`, http.StatusServiceUnavailable)
		return
	}

	payload, _ := json.Marshal(map[string]string{"conversationId": cascadeID})
	endpoint := fmt.Sprintf("https://127.0.0.1:%d/exa.language_server_pb.LanguageServerService/GetTrajectoryFileDiffs", port)
	req, err := http.NewRequestWithContext(r.Context(), http.MethodPost, endpoint, bytes.NewReader(payload))
	if err != nil {
		http.Error(w, `{"error":"failed to build upstream request"}`, http.StatusInternalServerError)
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Connect-Protocol-Version", "1")
	if token != "" {
		req.Header.Set("x-codeium-csrf-token", token)
	}

	client := p.longClient
	if client == nil {
		client = p.mediumClient
	}
	if client == nil {
		http.Error(w, `{"error":"Antigravity upstream client unavailable"}`, http.StatusServiceUnavailable)
		return
	}
	resp, err := client.Do(req)
	if err != nil {
		http.Error(w, `{"error":"failed to read current changes"}`, http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		http.Error(w, fmt.Sprintf(`{"error":"upstream GetTrajectoryFileDiffs returned %d: %s"}`,
			resp.StatusCode, strings.TrimSpace(string(body))), http.StatusBadGateway)
		return
	}

	var upstream struct {
		Diffs []struct {
			URI                   string `json:"uri"`
			OriginalContent       string `json:"originalContent"`
			ModifiedContent       string `json:"modifiedContent"`
			IsArtifactFile        bool   `json:"isArtifactFile"`
			IsKnowledgeFile       bool   `json:"isKnowledgeFile"`
			IsNoop                bool   `json:"isNoop"`
			FirstTouchedStepIndex *int   `json:"firstTouchedStepIndex"`
			LastTouchedStepIndex  *int   `json:"lastTouchedStepIndex"`
		} `json:"diffs"`
		UnavailableURIs []string `json:"unavailableUris"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&upstream); err != nil {
		http.Error(w, `{"error":"invalid GetTrajectoryFileDiffs response"}`, http.StatusBadGateway)
		return
	}

	result := CascadeDiffsResponse{CascadeID: cascadeID, Files: make([]CascadeDiffFile, 0, len(upstream.Diffs))}
	for _, item := range upstream.Diffs {
		if item.IsArtifactFile || item.IsKnowledgeFile || item.IsNoop || item.OriginalContent == item.ModifiedContent {
			continue
		}
		action := "MODIFY"
		if item.OriginalContent == "" {
			action = "CREATE"
		} else if item.ModifiedContent == "" {
			action = "DELETE"
		}
		result.Files = append(result.Files, CascadeDiffFile{
			FileURI: item.URI, FileName: diffFileName(item.URI), ActionType: action,
			OriginalContent: item.OriginalContent, ModifiedContent: item.ModifiedContent,
			FirstTouchedStepIndex: item.FirstTouchedStepIndex, LastTouchedStepIndex: item.LastTouchedStepIndex,
		})
	}
	result.UnavailableURIs = upstream.UnavailableURIs
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(result)
}
