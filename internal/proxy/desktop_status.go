package proxy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
)

type desktopStatusResponse struct {
	Permission desktopPermissionStatus `json:"permission"`
	MCPServers []desktopMCPServer      `json:"mcpServers"`
}

type desktopPermissionStatus struct {
	Preset                 string `json:"preset"`
	Source                 string `json:"source"`
	TerminalPolicy         string `json:"terminalPolicy,omitempty"`
	NonWorkspaceFilePolicy string `json:"nonWorkspaceFilePolicy,omitempty"`
	SandboxEnabled         *bool  `json:"sandboxEnabled,omitempty"`
	PlanReviewPolicy       string `json:"planReviewPolicy"`
}

type desktopMCPServer struct {
	Name                   string `json:"name"`
	Status                 string `json:"status"`
	Enabled                bool   `json:"enabled"`
	ToolCount              int    `json:"toolCount"`
	EnabledToolCount       int    `json:"enabledToolCount"`
	ToolErrorCount         int    `json:"toolErrorCount"`
	Error                  string `json:"error,omitempty"`
	RequiresAuthentication bool   `json:"requiresAuthentication,omitempty"`
}

func readDesktopPermissionStatus() (desktopPermissionStatus, error) {
	path, err := antigravityConfigPath()
	if err != nil {
		return desktopPermissionStatus{}, err
	}
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return desktopPermissionStatus{Preset: "DEFAULT", Source: "DEFAULT", PlanReviewPolicy: planReviewAlways}, nil
	}
	if err != nil {
		return desktopPermissionStatus{}, err
	}
	if len(data) > 4*1024*1024 {
		return desktopPermissionStatus{}, fmt.Errorf("Antigravity config is too large")
	}
	var config struct {
		UserSettings struct {
			PermissionPreset             string `json:"permissionPreset"`
			AutoExecutionPolicy          string `json:"autoExecutionPolicy"`
			NonWorkspaceFileAccessPolicy string `json:"nonWorkspaceFileAccessPolicy"`
			EnableTerminalSandbox        *bool  `json:"enableTerminalSandbox"`
			ArtifactReviewMode           string `json:"artifactReviewMode"`
		} `json:"userSettings"`
	}
	if err := json.Unmarshal(data, &config); err != nil {
		return desktopPermissionStatus{}, err
	}
	settings := config.UserSettings
	preset := permissionPreset(settings.PermissionPreset, settings.AutoExecutionPolicy)
	source := "PERMISSION_PRESET"
	if strings.TrimSpace(settings.PermissionPreset) == "" {
		source = "EFFECTIVE_SETTINGS"
	}
	planReview := planReviewAlways
	if strings.Contains(strings.ToUpper(settings.ArtifactReviewMode), planReviewTurbo) {
		planReview = planReviewTurbo
	}
	return desktopPermissionStatus{
		Preset: preset, Source: source,
		TerminalPolicy:         settings.AutoExecutionPolicy,
		NonWorkspaceFilePolicy: settings.NonWorkspaceFileAccessPolicy,
		SandboxEnabled:         settings.EnableTerminalSandbox,
		PlanReviewPolicy:       planReview,
	}, nil
}

// permissionPreset mirrors the desktop's preset fallback for Windows settings.
func permissionPreset(explicit, terminalPolicy string) string {
	upper := strings.ToUpper(explicit)
	for _, preset := range []string{"REQUEST_REVIEW", "DEFAULT", "VETTED", "TURBO", "AUTO"} {
		if strings.Contains(upper, preset) {
			return preset
		}
	}
	terminal := strings.ToUpper(terminalPolicy)
	if strings.Contains(terminal, "EAGER") {
		return "TURBO"
	}
	if strings.HasSuffix(terminal, "_AUTO") || terminal == "AUTO" {
		return "AUTO"
	}
	return "DEFAULT"
}

func rawJSONCount(value json.RawMessage) int {
	var list []json.RawMessage
	if json.Unmarshal(value, &list) == nil {
		count := 0
		for _, item := range list {
			if meaningfulJSONValue(item) {
				count++
			}
		}
		return count
	}
	var object map[string]json.RawMessage
	if json.Unmarshal(value, &object) == nil {
		count := 0
		for _, item := range object {
			if meaningfulJSONValue(item) {
				count++
			}
		}
		return count
	}
	return 0
}

func meaningfulJSONValue(value json.RawMessage) bool {
	trimmed := strings.TrimSpace(string(value))
	if trimmed == "" || trimmed == "null" || trimmed == "{}" || trimmed == "[]" || trimmed == `""` {
		return false
	}
	var text string
	return json.Unmarshal(value, &text) != nil || strings.TrimSpace(text) != ""
}

// HandleDesktopStatus returns a small read-only view of desktop permissions and MCP health.
func (p *Proxy) HandleDesktopStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	permission, err := readDesktopPermissionStatus()
	if err != nil {
		writeJSONError(w, "failed to read Antigravity permission settings", http.StatusInternalServerError)
		return
	}
	port, token := p.ActiveUpstream()
	if port == 0 || p.mediumClient == nil {
		writeJSONError(w, "Antigravity upstream not connected", http.StatusServiceUnavailable)
		return
	}
	endpoint := fmt.Sprintf("https://127.0.0.1:%d/exa.language_server_pb.LanguageServerService/GetMcpServerStates", port)
	upstream, err := http.NewRequestWithContext(r.Context(), http.MethodPost, endpoint, bytes.NewReader([]byte("{}")))
	if err != nil {
		writeJSONError(w, "failed to build MCP status request", http.StatusInternalServerError)
		return
	}
	upstream.Header.Set("Content-Type", "application/json")
	upstream.Header.Set("Connect-Protocol-Version", "1")
	if token != "" {
		upstream.Header.Set("x-codeium-csrf-token", token)
	}
	response, err := p.mediumClient.Do(upstream)
	if err != nil {
		writeJSONError(w, "failed to read MCP server states", http.StatusBadGateway)
		return
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
		writeJSONError(w, "Antigravity rejected the MCP status request", http.StatusBadGateway)
		return
	}
	var payload struct {
		States []struct {
			Spec struct {
				ServerName    string   `json:"serverName"`
				Disabled      bool     `json:"disabled"`
				DisabledTools []string `json:"disabledTools"`
			} `json:"spec"`
			Status string `json:"status"`
			Tools  []struct {
				Name string `json:"name"`
			} `json:"tools"`
			ToolErrors json.RawMessage `json:"toolErrors"`
			Error      string          `json:"error"`
			AuthURL    string          `json:"authUrl"`
		} `json:"states"`
	}
	if err := json.NewDecoder(io.LimitReader(response.Body, 8*1024*1024)).Decode(&payload); err != nil {
		writeJSONError(w, "invalid MCP status response", http.StatusBadGateway)
		return
	}
	servers := make([]desktopMCPServer, 0, len(payload.States))
	for _, state := range payload.States {
		enabledTools := len(state.Tools) - len(state.Spec.DisabledTools)
		if enabledTools < 0 {
			enabledTools = 0
		}
		servers = append(servers, desktopMCPServer{
			Name: strings.TrimSpace(state.Spec.ServerName), Status: state.Status,
			Enabled:   !state.Spec.Disabled && !strings.Contains(strings.ToUpper(state.Status), "DISABLED_BY_ADMIN"),
			ToolCount: len(state.Tools), EnabledToolCount: enabledTools,
			ToolErrorCount: rawJSONCount(state.ToolErrors), Error: strings.TrimSpace(state.Error),
			RequiresAuthentication: strings.TrimSpace(state.AuthURL) != "",
		})
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = json.NewEncoder(w).Encode(desktopStatusResponse{Permission: permission, MCPServers: servers})
}
