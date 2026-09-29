package proxy

import "testing"

func TestParseTrajectoryDetailsPreservesThinkingAndToolDetail(t *testing.T) {
	raw := &upstreamTrajectoryResp{}
	raw.Trajectory.Steps = make([]TrajectoryStep, 3)
	raw.Trajectory.Steps[0].Type = "CORTEX_STEP_TYPE_USER_INPUT"
	raw.Trajectory.Steps[0].UserInput = &TrajectoryUserInput{UserResponse: "test"}
	raw.Trajectory.Steps[1].Type = "CORTEX_STEP_TYPE_RUN_COMMAND"
	raw.Trajectory.Steps[1].Metadata.ToolSummary = "Inspect files"
	raw.Trajectory.Steps[1].RunCommand = &struct {
		CommandLine         string `json:"commandLine"`
		ProposedCommandLine string `json:"proposedCommandLine"`
		Cwd                 string `json:"cwd"`
		WaitMsBeforeAsync   string `json:"waitMsBeforeAsync"`
	}{CommandLine: "pwd"}
	raw.Trajectory.Steps[2].Type = "CORTEX_STEP_TYPE_PLANNER_RESPONSE"
	raw.Trajectory.Steps[2].PlannerResponse = &struct {
		Response string `json:"response"`
		Thinking string `json:"thinking"`
	}{Response: "answer", Thinking: "reasoning"}
	got := (&Proxy{}).ParseTrajectoryDetails(raw).AllMessages
	if len(got) < 4 {
		t.Fatalf("want user, tools, thought, answer; got %d messages", len(got))
	}
	if got[1].Type != "tools" || len(got[1].Details) != 1 || got[1].Details[0].Command != "pwd" {
		t.Fatalf("tool details lost: %#v", got[1])
	}
	if got[2].Type != "thought" || got[2].Text != "reasoning" {
		t.Fatalf("thinking lost: %#v", got[2])
	}
	if got[3].Type != "agent" || got[3].Text != "answer" {
		t.Fatalf("answer lost: %#v", got[3])
	}
}
