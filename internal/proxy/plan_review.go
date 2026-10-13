package proxy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

const (
	planReviewAlways = "ALWAYS"
	planReviewTurbo  = "TURBO"
)

type planReviewPreference struct {
	Policy                      string `json:"policy"`
	PlanningCommand             string `json:"planningCommand"`
	PlanningModeToggleSupported bool   `json:"planningModeToggleSupported"`
}

func antigravityConfigPath() (string, error) {
	if override := strings.TrimSpace(os.Getenv("ANTIGRAVITY_CONFIG_PATH")); override != "" {
		return override, nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".gemini", "config", "config.json"), nil
}

func readPlanReviewPolicy() (string, error) {
	path, err := antigravityConfigPath()
	if err != nil {
		return "", err
	}
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return planReviewAlways, nil
	}
	if err != nil {
		return "", err
	}
	if len(data) > 4*1024*1024 {
		return "", fmt.Errorf("Antigravity config is too large")
	}
	var config struct {
		UserSettings struct {
			ArtifactReviewMode string `json:"artifactReviewMode"`
		} `json:"userSettings"`
	}
	if err := json.Unmarshal(data, &config); err != nil {
		return "", err
	}
	if strings.Contains(strings.ToUpper(config.UserSettings.ArtifactReviewMode), planReviewTurbo) {
		return planReviewTurbo, nil
	}
	return planReviewAlways, nil
}

func writePlanReviewPreference(w http.ResponseWriter, policy string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(planReviewPreference{
		Policy: policy, PlanningCommand: "plan", PlanningModeToggleSupported: false,
	})
}

// HandlePlanReviewPreference reads or updates the same global setting used by desktop Agent Behavior.
func (p *Proxy) HandlePlanReviewPreference(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		policy, err := readPlanReviewPolicy()
		if err != nil {
			writeJSONError(w, "failed to read Antigravity plan review policy", http.StatusInternalServerError)
			return
		}
		writePlanReviewPreference(w, policy)
		return
	}
	if r.Method != http.MethodPut {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, 1024)
	var request struct {
		Policy string `json:"policy"`
	}
	if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
		writeJSONError(w, "invalid request payload", http.StatusBadRequest)
		return
	}
	request.Policy = strings.ToUpper(strings.TrimSpace(request.Policy))
	if request.Policy != planReviewAlways && request.Policy != planReviewTurbo {
		writeJSONError(w, "policy must be ALWAYS or TURBO", http.StatusBadRequest)
		return
	}

	port, token := p.ActiveUpstream()
	if port == 0 {
		writeJSONError(w, "Antigravity upstream not connected", http.StatusServiceUnavailable)
		return
	}
	payload, _ := json.Marshal(map[string]interface{}{
		"userConfig": map[string]interface{}{
			"userSettings": map[string]string{
				"artifactReviewMode": "ARTIFACT_REVIEW_MODE_" + request.Policy,
			},
		},
	})
	endpoint := fmt.Sprintf("https://127.0.0.1:%d/exa.language_server_pb.LanguageServerService/JetboxWriteState", port)
	upstream, err := http.NewRequestWithContext(r.Context(), http.MethodPost, endpoint, bytes.NewReader(payload))
	if err != nil {
		writeJSONError(w, "failed to build upstream request", http.StatusInternalServerError)
		return
	}
	upstream.Header.Set("Content-Type", "application/json")
	upstream.Header.Set("Connect-Protocol-Version", "1")
	if token != "" {
		upstream.Header.Set("x-codeium-csrf-token", token)
	}
	client := p.mediumClient
	if client == nil {
		writeJSONError(w, "Antigravity upstream client unavailable", http.StatusServiceUnavailable)
		return
	}
	response, err := client.Do(upstream)
	if err != nil {
		writeJSONError(w, "failed to update plan review policy", http.StatusBadGateway)
		return
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
		writeJSONError(w, "Antigravity rejected the plan review policy", http.StatusBadGateway)
		return
	}
	writePlanReviewPreference(w, request.Policy)
}
