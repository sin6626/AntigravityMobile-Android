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

func TestPlanReviewPreferenceReadsDesktopConfigAndWritesExactEnum(t *testing.T) {
	configPath := filepath.Join(t.TempDir(), "config.json")
	if err := os.WriteFile(configPath, []byte(`{"userSettings":{"artifactReviewMode":"ARTIFACT_REVIEW_MODE_TURBO"}}`), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("ANTIGRAVITY_CONFIG_PATH", configPath)

	var received map[string]interface{}
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/exa.language_server_pb.LanguageServerService/JetboxWriteState" {
			http.NotFound(w, r)
			return
		}
		_ = json.NewDecoder(r.Body).Decode(&received)
		_, _ = w.Write([]byte(`{}`))
	}))
	defer upstream.Close()
	p := &Proxy{mediumClient: upstream.Client()}
	p.SetTestUpstream(upstream.Listener.Addr().(*net.TCPAddr).Port, "test-token")

	read := httptest.NewRecorder()
	p.ServeHTTP(read, httptest.NewRequest(http.MethodGet, "/gateway/preferences/plan-review", nil))
	if read.Code != http.StatusOK || !json.Valid(read.Body.Bytes()) {
		t.Fatalf("unexpected read response: %d %s", read.Code, read.Body.String())
	}
	var current planReviewPreference
	_ = json.Unmarshal(read.Body.Bytes(), &current)
	if current.Policy != planReviewTurbo || current.PlanningCommand != "plan" || current.PlanningModeToggleSupported {
		t.Fatalf("unexpected preference: %#v", current)
	}

	update := httptest.NewRecorder()
	p.ServeHTTP(update, httptest.NewRequest(http.MethodPut, "/gateway/preferences/plan-review",
		strings.NewReader(`{"policy":"ALWAYS"}`)))
	if update.Code != http.StatusOK {
		t.Fatalf("unexpected update response: %d %s", update.Code, update.Body.String())
	}
	settings := received["userConfig"].(map[string]interface{})["userSettings"].(map[string]interface{})
	if settings["artifactReviewMode"] != "ARTIFACT_REVIEW_MODE_ALWAYS" {
		t.Fatalf("wrong upstream payload: %#v", received)
	}
}

func TestPlanReviewPreferenceRejectsUnknownPolicy(t *testing.T) {
	recorder := httptest.NewRecorder()
	(&Proxy{}).ServeHTTP(recorder, httptest.NewRequest(http.MethodPut, "/gateway/preferences/plan-review",
		strings.NewReader(`{"policy":"AUTO"}`)))
	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", recorder.Code)
	}
}
