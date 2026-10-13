package proxy

import (
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestHandleSlashCommandsUsesSelectedModelAndWorkspace(t *testing.T) {
	var received map[string]interface{}
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/GetSlashCommands") {
			http.NotFound(w, r)
			return
		}
		_ = json.NewDecoder(r.Body).Decode(&received)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"commands":[{"info":{"name":"plan","modelFacingText":"<PLAN />","type":"SLASH_COMMAND_TYPE_SYSTEM","icon":"ballot"},"title":"plan","description":"Plan carefully."}]}`))
	}))
	defer upstream.Close()
	p := &Proxy{shortClient: upstream.Client(), mediumClient: upstream.Client(), longClient: upstream.Client()}
	p.activePort = upstream.Listener.Addr().(*net.TCPAddr).Port
	p.activeToken = "secret"

	req := httptest.NewRequest(http.MethodGet, "/gateway/commands?model=MODEL_PLACEHOLDER_M318&workspaceUri=file%3A%2F%2FD%3A%2FProject", nil)
	rec := httptest.NewRecorder()
	p.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	planner := received["cascadeConfig"].(map[string]interface{})["plannerConfig"].(map[string]interface{})
	if planner["planModel"] != "MODEL_PLACEHOLDER_M318" {
		t.Fatalf("selected model was not applied: %#v", planner)
	}
	if got := received["workspaceUris"].([]interface{}); len(got) != 1 || got[0] != "file:///d:/Project" {
		t.Fatalf("unexpected workspaceUris: %#v", got)
	}
	if !strings.Contains(rec.Body.String(), `"name":"plan"`) {
		t.Fatalf("missing command response: %s", rec.Body.String())
	}
}

func TestHandleSlashCommandsValidatesRequest(t *testing.T) {
	for _, tc := range []struct {
		method string
		target string
		want   int
	}{
		{http.MethodPost, "/gateway/commands?model=MODEL_PLACEHOLDER_M318", http.StatusMethodNotAllowed},
		{http.MethodGet, "/gateway/commands?model=unknown", http.StatusServiceUnavailable},
	} {
		p := &Proxy{}
		rec := httptest.NewRecorder()
		p.ServeHTTP(rec, httptest.NewRequest(tc.method, tc.target, nil))
		if rec.Code != tc.want {
			t.Fatalf("%s %s: want %d, got %d", tc.method, tc.target, tc.want, rec.Code)
		}
	}
}
