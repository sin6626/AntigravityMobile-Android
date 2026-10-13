package proxy

import (
	"encoding/json"
	"net/http"
	"sort"
	"strings"
)

type SubagentItem struct {
	ID                   string `json:"id"`
	ParentConversationID string `json:"parentConversationId"`
	RootConversationID   string `json:"rootConversationId,omitempty"`
	Title                string `json:"title"`
	Status               string `json:"status"`
	StepCount            int    `json:"stepCount"`
	LastModifiedTime     string `json:"lastModifiedTime,omitempty"`
	NestingDepth         int    `json:"nestingDepth"`
	NeedsInput           bool   `json:"needsInput"`
	HasError             bool   `json:"hasError"`
}

// HandleSubagents returns only direct children so the mobile client can navigate the same hierarchy as desktop.
func (p *Proxy) HandleSubagents(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	parentID := strings.TrimSpace(r.URL.Query().Get("parentId"))
	if !cascadeIDRe.MatchString(parentID) {
		http.Error(w, "invalid parentId", http.StatusBadRequest)
		return
	}

	port, token := p.ActiveUpstream()
	if port <= 0 {
		http.Error(w, "upstream unavailable", http.StatusServiceUnavailable)
		return
	}
	trajectories, err := p.fetchTrajectoriesSummary(port, token)
	if err != nil {
		http.Error(w, "failed to fetch subagents", http.StatusBadGateway)
		return
	}

	items := make([]SubagentItem, 0)
	for id, summary := range trajectories {
		metadata := summary.TrajectoryMetadata
		if metadata == nil || metadata.ParentConversationID != parentID {
			continue
		}
		title := summary.FullTitle
		if summary.Annotations != nil && strings.TrimSpace(summary.Annotations.Title) != "" {
			title = summary.Annotations.Title
		} else if strings.TrimSpace(title) == "" {
			title = summary.Summary
		}
		if strings.TrimSpace(title) == "" {
			title = "未命名子代理"
		}
		items = append(items, SubagentItem{
			ID: id, ParentConversationID: parentID, RootConversationID: metadata.RootConversationID,
			Title: title, Status: summary.Status, StepCount: summary.StepCount,
			LastModifiedTime: summary.LastModifiedTime, NestingDepth: metadata.NestingDepth,
			NeedsInput: summary.NeedsInput, HasError: summary.HasError,
		})
	}
	sort.Slice(items, func(i, j int) bool {
		priority := func(item SubagentItem) int {
			if item.NeedsInput {
				return 3
			}
			if strings.Contains(strings.ToUpper(item.Status), "RUNNING") {
				return 2
			}
			if item.HasError || strings.Contains(strings.ToUpper(item.Status), "ERROR") || strings.Contains(strings.ToUpper(item.Status), "FAIL") {
				return 1
			}
			return 0
		}
		left, right := priority(items[i]), priority(items[j])
		if left != right {
			return left > right
		}
		if items[i].LastModifiedTime != items[j].LastModifiedTime {
			return items[i].LastModifiedTime > items[j].LastModifiedTime
		}
		return items[i].ID < items[j].ID
	})

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{"subagents": items})
}
