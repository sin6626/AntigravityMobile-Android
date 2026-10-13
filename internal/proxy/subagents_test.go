package proxy

import (
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHandleSubagentsReturnsDirectChildrenInActionOrder(t *testing.T) {
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/exa.language_server_pb.LanguageServerService/GetAllCascadeTrajectories" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"trajectorySummaries":{
			"done":{"summary":"完成检查","status":"IDLE","stepCount":3,"lastModifiedTime":"2026-10-13T08:03:00Z","trajectoryMetadata":{"parentConversationId":"parent","rootConversationId":"parent","nestingDepth":1}},
			"waiting":{"fullTitle":"等待选择","status":"WAITING","needsInput":true,"lastModifiedTime":"2026-10-13T08:01:00Z","trajectoryMetadata":{"parentConversationId":"parent","rootConversationId":"parent","nestingDepth":1}},
			"nested":{"summary":"孙级","trajectoryMetadata":{"parentConversationId":"done","rootConversationId":"parent","nestingDepth":2}},
			"other":{"summary":"其他会话","trajectoryMetadata":{"parentConversationId":"another","nestingDepth":1}}
		}}`))
	}))
	defer upstream.Close()
	p := &Proxy{shortClient: upstream.Client(), mediumClient: upstream.Client(), longClient: upstream.Client()}
	p.SetTestUpstream(upstream.Listener.Addr().(*net.TCPAddr).Port, "test-token")

	req := httptest.NewRequest(http.MethodGet, "/gateway/subagents?parentId=parent", nil)
	rec := httptest.NewRecorder()
	p.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	var response struct {
		Subagents []SubagentItem `json:"subagents"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &response); err != nil {
		t.Fatal(err)
	}
	if len(response.Subagents) != 2 {
		t.Fatalf("expected 2 direct children, got %#v", response.Subagents)
	}
	if response.Subagents[0].ID != "waiting" || response.Subagents[1].ID != "done" {
		t.Fatalf("expected action-first order, got %#v", response.Subagents)
	}
}

func TestHandleSubagentsValidatesRequest(t *testing.T) {
	p := &Proxy{}
	for _, tc := range []struct {
		method, target string
		want           int
	}{
		{http.MethodPost, "/gateway/subagents?parentId=parent", http.StatusMethodNotAllowed},
		{http.MethodGet, "/gateway/subagents?parentId=../bad", http.StatusBadRequest},
		{http.MethodGet, "/gateway/subagents?parentId=parent", http.StatusServiceUnavailable},
	} {
		rec := httptest.NewRecorder()
		p.ServeHTTP(rec, httptest.NewRequest(tc.method, tc.target, nil))
		if rec.Code != tc.want {
			t.Errorf("%s %s: want %d, got %d", tc.method, tc.target, tc.want, rec.Code)
		}
	}
}
