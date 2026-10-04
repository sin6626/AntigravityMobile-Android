package proxy

import (
	"encoding/json"
	"fmt"
	"strings"
)

func revertStepReason(raw *upstreamTrajectoryResp, index int) string {
	if index < 0 || index >= len(raw.Trajectory.Steps) {
		return "消息步骤不存在，请刷新会话"
	}
	step := raw.Trajectory.Steps[index]
	if step.Type != "CORTEX_STEP_TYPE_USER_INPUT" || step.UserInput == nil {
		return "只能回退已发送的用户消息"
	}
	if strings.Contains(strings.ToUpper(step.Status), "CLEARED") {
		return "这条消息已被清理，无法回退"
	}
	input := step.UserInput
	if len(input.ArtifactComments)+len(input.FileDiffComments)+len(input.FileComments) > 0 {
		return "请在电脑端回退包含评审评论的消息"
	}
	// ponytail: Best of N stays on desktop until mobile can restore its branches and review state.
	battle := strings.TrimSpace(string(raw.Trajectory.Metadata.BattleModeMetadata))
	if len(raw.Trajectory.BattleModeInfos) > 0 || raw.Trajectory.Metadata.IsBattleModeFork ||
		(battle != "" && battle != "{}" && battle != "null") {
		return "请在电脑端回退 Best of N 会话"
	}
	return ""
}

func (p *Proxy) validateRevertTarget(id string, index int, override *int, port int, token string) (*upstreamTrajectoryResp, int, error) {
	target := index - 1
	if index < 0 || (override != nil && *override != target) {
		return nil, 0, fmt.Errorf("无效的回退步骤")
	}
	raw, err := p.fetchUpstreamTrajectoryWithMaxAge(id, port, token, 0)
	if err != nil {
		return nil, 0, err
	}
	if reason := revertStepReason(raw, index); reason != "" {
		return nil, 0, fmt.Errorf("%s", reason)
	}
	return raw, target, nil
}

func revertConfig(raw *upstreamTrajectoryResp, id string) (json.RawMessage, error) {
	candidates := []json.RawMessage{}
	for i := len(raw.Trajectory.ExecutorMetadatas) - 1; i >= 0; i-- {
		candidates = append(candidates, raw.Trajectory.ExecutorMetadatas[i].CascadeConfig)
	}
	for i := len(raw.Trajectory.Steps) - 1; i >= 0; i-- {
		if input := raw.Trajectory.Steps[i].UserInput; input != nil {
			candidates = append(candidates, input.UserConfig, input.LastUserConfig)
		}
	}
	_, recorded := GetCascadeModel(id)
	candidates = append(candidates, recorded)
	for _, cfg := range candidates {
		var obj struct {
			PlannerConfig struct {
				PlanModel      interface{}            `json:"planModel"`
				RequestedModel map[string]interface{} `json:"requestedModel"`
			} `json:"plannerConfig"`
		}
		if json.Unmarshal(cfg, &obj) != nil {
			continue
		}
		model := obj.PlannerConfig.PlanModel
		if model == nil {
			model = obj.PlannerConfig.RequestedModel["model"]
		}
		if model != nil && fmt.Sprint(model) != "" && fmt.Sprint(model) != "0" && !strings.Contains(fmt.Sprint(model), "UNSPECIFIED") {
			return cfg, nil
		}
	}
	return nil, fmt.Errorf("无法确认会话的原模型配置，请在电脑端回退")
}
