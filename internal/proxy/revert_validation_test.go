package proxy

import (
	"bytes"
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestRevertValidationAndModelPreservation(t *testing.T) {
	raw := `{"status":"IDLE","trajectory":{"steps":[{"type":"CORTEX_STEP_TYPE_USER_INPUT","userInput":{}},{"type":"CORTEX_STEP_TYPE_PLANNER_RESPONSE"}],"executorMetadatas":[{"cascadeConfig":{"plannerConfig":{"planModel":"MODEL_PLACEHOLDER_M26","modelName":"original","requestedModel":{"model":"MODEL_PLACEHOLDER_M26"}},"checkpointConfig":{"maxTokenLimit":12345}}}]}}`
	current := raw
	calls := 0
	var executed map[string]interface{}
	upstream := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch {
		case strings.HasSuffix(r.URL.Path, "/GetCascadeTrajectory"):
			w.Write([]byte(current))
		case strings.HasSuffix(r.URL.Path, "/GetRevertPreview"):
			w.Write([]byte(`{"codeEditPreviews":[]}`))
		case strings.HasSuffix(r.URL.Path, "/RevertToCascadeStep"):
			calls++
			json.NewDecoder(r.Body).Decode(&executed)
			w.Write([]byte(`{}`))
		default:
			http.NotFound(w, r)
		}
	}))
	defer upstream.Close()
	p := &Proxy{shortClient: upstream.Client(), mediumClient: upstream.Client(), longClient: upstream.Client()}
	p.SetTestUpstream(upstream.Listener.Addr().(*net.TCPAddr).Port, "test")
	cases := []struct{ name, body, trajectory string }{
		{"missing", `{"cascadeId":"revert-validation"}`, raw},
		{"negative", `{"cascadeId":"revert-validation","stepIndex":-1}`, raw},
		{"outside", `{"cascadeId":"revert-validation","stepIndex":9}`, raw},
		{"agent", `{"cascadeId":"revert-validation","stepIndex":1}`, raw},
		{"override", `{"cascadeId":"revert-validation","stepIndex":0,"targetStepIndex":5}`, raw},
		{"running", `{"cascadeId":"revert-validation","stepIndex":0}`, strings.Replace(raw, `"IDLE"`, `"RUNNING"`, 1)},
		{"cleared", `{"cascadeId":"revert-validation","stepIndex":0}`, strings.Replace(raw, `"userInput":{}`, `"status":"CORTEX_STEP_STATUS_CLEARED","userInput":{}`, 1)},
		{"comments", `{"cascadeId":"revert-validation","stepIndex":0}`, strings.Replace(raw, `"userInput":{}`, `"userInput":{"fileComments":[{}]}`, 1)},
		{"battle", `{"cascadeId":"revert-validation","stepIndex":0}`, strings.Replace(raw, `"steps":`, `"battleModeInfos":[{}],"steps":`, 1)},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			current = tc.trajectory
			for _, preview := range []bool{true, false} {
				w := httptest.NewRecorder()
				req := httptest.NewRequest("POST", "/", bytes.NewBufferString(tc.body))
				if preview {
					p.HandleCascadeRevertPreview(w, req)
				} else {
					p.HandleCascadeRevertExecute(w, req)
				}
				if w.Code == 200 {
					t.Fatalf("unsafe request accepted: %s", w.Body.String())
				}
			}
			if calls != 0 {
				t.Fatal("invalid request executed a revert")
			}
		})
	}
	current = strings.Replace(raw, `"plannerConfig"`, `"missingPlannerConfig"`, 1)
	w := httptest.NewRecorder()
	p.HandleCascadeRevertExecute(w, httptest.NewRequest("POST", "/", strings.NewReader(`{"cascadeId":"revert-validation","stepIndex":0}`)))
	if w.Code == 200 || calls != 0 {
		t.Fatal("missing model used a default")
	}
	current = strings.Replace(raw, `"steps":`, `"metadata":{"battleModeMetadata":null},"steps":`, 1)
	w = httptest.NewRecorder()
	p.HandleCascadeRevertExecute(w, httptest.NewRequest("POST", "/", strings.NewReader(`{"cascadeId":"revert-validation","stepIndex":0,"conversationOnly":true}`)))
	if w.Code != 200 || calls != 1 {
		t.Fatalf("valid revert failed: %s", w.Body.String())
	}
	cfg := executed["overrideConfig"].(map[string]interface{})
	if cfg["plannerConfig"].(map[string]interface{})["modelName"] != "original" || cfg["checkpointConfig"].(map[string]interface{})["maxTokenLimit"] != float64(12345) || executed["conversationOnly"] != true || executed["stepIndex"] != float64(-1) {
		t.Fatalf("configuration changed: %v", executed)
	}
}
