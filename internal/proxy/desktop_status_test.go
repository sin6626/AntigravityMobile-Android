package proxy

import (
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestDesktopStatusSanitizesMCPAndReadsDesktopPermissions(t *testing.T) {
	configPath := filepath.Join(t.TempDir(), "config.json")
	if err := os.WriteFile(configPath, []byte(`{"userSettings":{"autoExecutionPolicy":"CASCADE_COMMANDS_AUTO_EXECUTION_EAGER","nonWorkspaceFileAccessPolicy":"AGENT_SETTING_POLICY_ALLOW","enableTerminalSandbox":false,"artifactReviewMode":"ARTIFACT_REVIEW_MODE_ALWAYS"}}`), 0600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("ANTIGRAVITY_CONFIG_PATH", configPath)
	var gotToken string
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotToken = r.Header.Get("x-codeium-csrf-token")
		if r.URL.Path != "/exa.language_server_pb.LanguageServerService/GetMcpServerStates" {
			http.NotFound(w, r)
			return
		}
		_, _ = w.Write([]byte(`{"states":[
			{"spec":{"serverName":"cua-driver","command":"secret-command","disabledTools":["hidden"]},"status":"MCP_SERVER_STATUS_READY","tools":[{"name":"click","description":"private schema"},{"name":"hidden"}],"toolErrors":["",""]},
			{"spec":{"serverName":"broken"},"status":"MCP_SERVER_STATUS_ERROR","tools":[],"toolErrors":{"start":"schema failed"},"error":"process exited","authUrl":"https://secret.example/token"}
		]}`))
	}))
	defer upstream.Close()
	p := &Proxy{shortClient: upstream.Client(), mediumClient: upstream.Client(), longClient: upstream.Client()}
	p.activePort = upstream.Listener.Addr().(*net.TCPAddr).Port
	p.activeToken = "csrf-secret"

	recorder := httptest.NewRecorder()
	p.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/gateway/desktop/status", nil))
	if recorder.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", recorder.Code, recorder.Body.String())
	}
	if gotToken != "csrf-secret" {
		t.Fatalf("missing upstream token: %q", gotToken)
	}
	var status desktopStatusResponse
	if err := json.Unmarshal(recorder.Body.Bytes(), &status); err != nil {
		t.Fatal(err)
	}
	if status.Permission.Preset != "TURBO" || status.Permission.Source != "EFFECTIVE_SETTINGS" || status.Permission.PlanReviewPolicy != "ALWAYS" {
		t.Fatalf("unexpected permission status: %#v", status.Permission)
	}
	if len(status.MCPServers) != 2 || status.MCPServers[0].EnabledToolCount != 1 || status.MCPServers[0].ToolErrorCount != 0 {
		t.Fatalf("unexpected MCP summary: %#v", status.MCPServers)
	}
	if !status.MCPServers[1].RequiresAuthentication || status.MCPServers[1].ToolErrorCount != 1 || !strings.Contains(status.MCPServers[1].Error, "exited") {
		t.Fatalf("missing MCP failure state: %#v", status.MCPServers[1])
	}
	for _, secret := range []string{"secret-command", "private schema", "secret.example"} {
		if strings.Contains(recorder.Body.String(), secret) {
			t.Fatalf("response leaked %q: %s", secret, recorder.Body.String())
		}
	}
}

func TestDesktopStatusValidatesMethodAndConnection(t *testing.T) {
	t.Setenv("ANTIGRAVITY_CONFIG_PATH", filepath.Join(t.TempDir(), "missing.json"))
	for _, tc := range []struct {
		method string
		want   int
	}{
		{http.MethodPost, http.StatusMethodNotAllowed},
		{http.MethodGet, http.StatusServiceUnavailable},
	} {
		recorder := httptest.NewRecorder()
		(&Proxy{}).ServeHTTP(recorder, httptest.NewRequest(tc.method, "/gateway/desktop/status", nil))
		if recorder.Code != tc.want {
			t.Errorf("%s: want %d, got %d", tc.method, tc.want, recorder.Code)
		}
	}
}
