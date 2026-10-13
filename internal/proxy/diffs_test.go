package proxy

import (
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHandleCascadeDiffsUsesDesktopContractAndFiltersArtifacts(t *testing.T) {
	var received map[string]interface{}
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/exa.language_server_pb.LanguageServerService/GetTrajectoryFileDiffs" {
			http.NotFound(w, r)
			return
		}
		_ = json.NewDecoder(r.Body).Decode(&received)
		_, _ = w.Write([]byte(`{"diffs":[
			{"uri":"file:///workspace/Agent.md","originalContent":"old","modifiedContent":"new","firstTouchedStepIndex":2,"lastTouchedStepIndex":4},
			{"uri":"file:///brain/plan.md","modifiedContent":"plan","isArtifactFile":true},
			{"uri":"file:///workspace/same.txt","originalContent":"same","modifiedContent":"same"}
		],"unavailableUris":["file:///workspace/gone.kt"]}`))
	}))
	defer upstream.Close()
	p := &Proxy{shortClient: upstream.Client(), mediumClient: upstream.Client(), longClient: upstream.Client()}
	p.SetTestUpstream(upstream.Listener.Addr().(*net.TCPAddr).Port, "test-token")

	rec := httptest.NewRecorder()
	p.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/gateway/cascade/diffs?cascadeId=cascade-1", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if received["conversationId"] != "cascade-1" {
		t.Fatalf("wrong upstream request: %#v", received)
	}
	var response CascadeDiffsResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if len(response.Files) != 1 || response.Files[0].FileName != "Agent.md" || response.Files[0].ActionType != "MODIFY" {
		t.Fatalf("unexpected filtered response: %#v", response)
	}
	if len(response.UnavailableURIs) != 1 {
		t.Fatalf("missing unavailable URI: %#v", response)
	}
}

func TestHandleCascadeDiffsValidatesRequest(t *testing.T) {
	for _, tc := range []struct {
		method, target string
		want           int
	}{
		{http.MethodPost, "/gateway/cascade/diffs?cascadeId=cascade-1", http.StatusMethodNotAllowed},
		{http.MethodGet, "/gateway/cascade/diffs?cascadeId=../bad", http.StatusBadRequest},
		{http.MethodGet, "/gateway/cascade/diffs?cascadeId=cascade-1", http.StatusServiceUnavailable},
	} {
		rec := httptest.NewRecorder()
		(&Proxy{}).ServeHTTP(rec, httptest.NewRequest(tc.method, tc.target, nil))
		if rec.Code != tc.want {
			t.Errorf("%s %s: want %d, got %d", tc.method, tc.target, tc.want, rec.Code)
		}
	}
}
