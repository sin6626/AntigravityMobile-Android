package proxy

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

type SlashCommandArgument struct {
	Name         string `json:"name"`
	Description  string `json:"description,omitempty"`
	Required     bool   `json:"required,omitempty"`
	DefaultValue string `json:"defaultValue,omitempty"`
}

type SlashCommandInfo struct {
	Name            string          `json:"name"`
	ModelFacingText string          `json:"modelFacingText"`
	Type            string          `json:"type,omitempty"`
	Icon            string          `json:"icon,omitempty"`
	DefinitionPath  string          `json:"definitionPath,omitempty"`
	DisplayName     string          `json:"displayName,omitempty"`
	Logo            json.RawMessage `json:"logo,omitempty"`
	Plugin          json.RawMessage `json:"plugin,omitempty"`
}

type SlashCommandDefinition struct {
	Info        SlashCommandInfo       `json:"info"`
	Title       string                 `json:"title,omitempty"`
	Description string                 `json:"description,omitempty"`
	Arguments   []SlashCommandArgument `json:"arguments,omitempty"`
}

// HandleSlashCommands supplies the same command definitions accepted by a
// slashCommand ContextScopeItem in SendUserCascadeMessage.
func (p *Proxy) HandleSlashCommands(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	port, token := p.ActiveUpstream()
	if port == 0 {
		http.Error(w, "Antigravity upstream unavailable", http.StatusServiceUnavailable)
		return
	}
	cascadeID := strings.TrimSpace(r.URL.Query().Get("cascadeId"))
	if cascadeID != "" && !cascadeIDRe.MatchString(cascadeID) {
		http.Error(w, "invalid cascadeId", http.StatusBadRequest)
		return
	}
	modelEnum := resolveModelEnum(r.URL.Query().Get("model"))
	if modelEnum == "" {
		http.Error(w, "valid model is required", http.StatusBadRequest)
		return
	}

	var config interface{}
	if raw := p.GetCascadeConfig(cascadeID, port, token); len(raw) > 0 {
		_ = json.Unmarshal(raw, &config)
	}
	config = applyModelToCascadeConfig(config, modelEnum, canonicalModelName(modelEnum))
	workspaceURIs := []string{}
	if workspaceURI := normalizeURI(r.URL.Query().Get("workspaceUri")); workspaceURI != "" {
		workspaceURIs = append(workspaceURIs, workspaceURI)
	}
	payload, _ := json.Marshal(map[string]interface{}{
		"cascadeConfig": config,
		"cascadeId":     cascadeID,
		"workspaceUris": workspaceURIs,
	})
	requestURL := fmt.Sprintf("https://127.0.0.1:%d/exa.language_server_pb.LanguageServerService/GetSlashCommands", port)
	req, err := http.NewRequestWithContext(r.Context(), http.MethodPost, requestURL, strings.NewReader(string(payload)))
	if err != nil {
		http.Error(w, "failed to build command request", http.StatusInternalServerError)
		return
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Connect-Protocol-Version", "1")
	if token != "" {
		req.Header.Set("x-codeium-csrf-token", token)
	}
	resp, err := p.mediumClient.Do(req)
	if err != nil {
		http.Error(w, "failed to load commands", http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		http.Error(w, "failed to load commands", http.StatusBadGateway)
		return
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2*1024*1024+1))
	if err != nil || len(body) > 2*1024*1024 {
		http.Error(w, "invalid command response", http.StatusBadGateway)
		return
	}
	var result struct {
		Commands []SlashCommandDefinition `json:"commands"`
	}
	if err := json.Unmarshal(body, &result); err != nil || len(result.Commands) > 200 {
		http.Error(w, "invalid command response", http.StatusBadGateway)
		return
	}
	filtered := result.Commands[:0]
	for _, command := range result.Commands {
		command.Info.Name = strings.TrimSpace(command.Info.Name)
		if command.Info.Name != "" {
			filtered = append(filtered, command)
		}
	}
	data, _ := json.Marshal(map[string]interface{}{"commands": filtered})
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Length", fmt.Sprint(len(data)))
	_, _ = w.Write(data)
}
