package proxy

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

const (
	defaultWorkspaceContextLimit = 30
	maxWorkspaceContextLimit     = 50
	maxWorkspaceEntriesScanned   = 50_000
	maxWorkspaceConfigSize       = 1024 * 1024
)

var errWorkspaceSearchLimit = errors.New("workspace search limit reached")

type WorkspacePathScope struct {
	AbsoluteURI                  string            `json:"absoluteUri"`
	WorkspaceURIsToRelativePaths map[string]string `json:"workspaceUrisToRelativePaths"`
	NumFiles                     uint32            `json:"numFiles,omitempty"`
	NumBytes                     uint64            `json:"numBytes,omitempty"`
}

type WorkspaceScopeItem struct {
	File      *WorkspacePathScope `json:"file,omitempty"`
	Directory *WorkspacePathScope `json:"directory,omitempty"`
}

type WorkspaceContextItem struct {
	Type         string             `json:"type"`
	Name         string             `json:"name"`
	RelativePath string             `json:"relativePath"`
	RootName     string             `json:"rootName"`
	RootURI      string             `json:"rootUri"`
	URI          string             `json:"uri"`
	Size         int64              `json:"size,omitempty"`
	ScopeItem    WorkspaceScopeItem `json:"scopeItem"`
	score        int
}

type WorkspaceContextResponse struct {
	WorkspaceURI string                 `json:"workspaceUri"`
	Items        []WorkspaceContextItem `json:"items"`
	NextCursor   string                 `json:"nextCursor,omitempty"`
	Truncated    bool                   `json:"truncated,omitempty"`
}

type workspaceRoot struct {
	name string
	path string
	uri  string
}

// HandleWorkspaceContext searches only the selected local workspace and returns
// the exact scope item shape accepted by SendUserCascadeMessageRequest.items.
func (p *Proxy) HandleWorkspaceContext(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	workspaceURI := normalizeURI(r.URL.Query().Get("workspaceUri"))
	if workspaceURI == "" {
		http.Error(w, "workspaceUri is required", http.StatusBadRequest)
		return
	}
	limit := defaultWorkspaceContextLimit
	if raw := strings.TrimSpace(r.URL.Query().Get("limit")); raw != "" {
		value, err := strconv.Atoi(raw)
		if err != nil || value < 1 || value > maxWorkspaceContextLimit {
			http.Error(w, "limit must be between 1 and 50", http.StatusBadRequest)
			return
		}
		limit = value
	}
	offset, err := decodeWorkspaceCursor(r.URL.Query().Get("cursor"))
	if err != nil {
		http.Error(w, "invalid cursor", http.StatusBadRequest)
		return
	}

	items, truncated, err := searchWorkspaceContext(workspaceURI, r.URL.Query().Get("query"))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	if offset > len(items) {
		http.Error(w, "invalid cursor", http.StatusBadRequest)
		return
	}
	end := min(offset+limit, len(items))
	response := WorkspaceContextResponse{WorkspaceURI: workspaceURI, Items: items[offset:end], Truncated: truncated}
	if end < len(items) {
		response.NextCursor = base64.RawURLEncoding.EncodeToString([]byte(strconv.Itoa(end)))
	}
	data, err := json.Marshal(response)
	if err != nil {
		http.Error(w, "failed to encode response", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Content-Length", strconv.Itoa(len(data)))
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(data)
}

func decodeWorkspaceCursor(raw string) (int, error) {
	if strings.TrimSpace(raw) == "" {
		return 0, nil
	}
	decoded, err := base64.RawURLEncoding.DecodeString(raw)
	if err != nil {
		return 0, err
	}
	offset, err := strconv.Atoi(string(decoded))
	if err != nil || offset < 0 {
		return 0, errors.New("invalid cursor")
	}
	return offset, nil
}

func searchWorkspaceContext(workspaceURI, query string) ([]WorkspaceContextItem, bool, error) {
	roots, err := resolveWorkspaceRoots(workspaceURI)
	if err != nil {
		return nil, false, err
	}
	query = strings.ToLower(strings.TrimSpace(query))
	items := make([]WorkspaceContextItem, 0, defaultWorkspaceContextLimit)
	visited := 0
	truncated := false
	for _, root := range roots {
		err := filepath.WalkDir(root.path, func(path string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				if entry != nil && entry.IsDir() {
					return fs.SkipDir
				}
				return nil
			}
			if path == root.path {
				return nil
			}
			visited++
			if visited > maxWorkspaceEntriesScanned {
				return errWorkspaceSearchLimit
			}
			if entry.Type()&os.ModeSymlink != 0 {
				if entry.IsDir() {
					return fs.SkipDir
				}
				return nil
			}
			relative, err := filepath.Rel(root.path, path)
			if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
				return nil
			}
			relative = filepath.ToSlash(relative)
			if entry.IsDir() && ignoredWorkspaceDirectory(entry.Name()) {
				return fs.SkipDir
			}
			if query == "" && strings.Contains(relative, "/") {
				if entry.IsDir() {
					return fs.SkipDir
				}
				return nil
			}
			score, matched := workspaceMatchScore(relative, entry.Name(), query)
			if !matched || !IsSafeFilePath(path) {
				if entry.IsDir() && query == "" {
					return fs.SkipDir
				}
				return nil
			}
			info, err := entry.Info()
			if err != nil || (!entry.IsDir() && !info.Mode().IsRegular()) {
				return nil
			}
			uri := normalizeURI(path)
			scope := &WorkspacePathScope{
				AbsoluteURI:                  uri,
				WorkspaceURIsToRelativePaths: map[string]string{root.uri: relative},
			}
			item := WorkspaceContextItem{
				Name: entry.Name(), RelativePath: relative, RootName: root.name,
				RootURI: root.uri, URI: uri, score: score,
			}
			if entry.IsDir() {
				item.Type = "directory"
				item.ScopeItem.Directory = scope
			} else {
				item.Type = "file"
				item.Size = info.Size()
				scope.NumFiles = 1
				scope.NumBytes = uint64(info.Size())
				item.ScopeItem.File = scope
			}
			items = append(items, item)
			if entry.IsDir() && query == "" {
				return fs.SkipDir
			}
			return nil
		})
		if errors.Is(err, errWorkspaceSearchLimit) {
			truncated = true
			break
		}
		if err != nil {
			return nil, false, fmt.Errorf("failed to search workspace")
		}
	}
	sort.Slice(items, func(i, j int) bool {
		if items[i].score != items[j].score {
			return items[i].score < items[j].score
		}
		if items[i].Type != items[j].Type {
			return items[i].Type == "directory"
		}
		left := strings.ToLower(items[i].RootName + "/" + items[i].RelativePath)
		right := strings.ToLower(items[j].RootName + "/" + items[j].RelativePath)
		return left < right
	})
	return items, truncated, nil
}

func workspaceMatchScore(relative, name, query string) (int, bool) {
	if query == "" {
		return 0, true
	}
	name = strings.ToLower(name)
	relative = strings.ToLower(relative)
	switch {
	case name == query:
		return 0, true
	case strings.HasPrefix(name, query):
		return 1, true
	case strings.Contains(name, query):
		return 2, true
	case strings.Contains(relative, query):
		return 3, true
	default:
		return 0, false
	}
}

func ignoredWorkspaceDirectory(name string) bool {
	switch strings.ToLower(name) {
	case ".git", ".gradle", ".idea", ".next", ".cache", ".verification", ".user_uploaded",
		"node_modules", "build", "dist", "out", "target", "coverage", ".venv", "venv", "__pycache__":
		return true
	default:
		return false
	}
}

func resolveWorkspaceRoots(workspaceURI string) ([]workspaceRoot, error) {
	if isRemoteURI(workspaceURI) {
		return nil, errors.New("remote workspaces are not supported")
	}
	workspacePath := uriToPath(workspaceURI)
	if resolved, err := filepath.EvalSymlinks(workspacePath); err == nil {
		workspacePath = resolved
	}
	if !IsSafeFilePath(workspacePath) {
		return nil, errors.New("workspace path is restricted")
	}
	info, err := os.Stat(workspacePath)
	if err != nil {
		return nil, errors.New("workspace not found")
	}
	if info.IsDir() {
		return []workspaceRoot{{name: filepath.Base(workspacePath), path: workspacePath, uri: normalizeURI(workspacePath)}}, nil
	}
	if !info.Mode().IsRegular() || !strings.EqualFold(filepath.Ext(workspacePath), ".code-workspace") || info.Size() > maxWorkspaceConfigSize {
		return nil, errors.New("workspaceUri must point to a directory or .code-workspace file")
	}
	data, err := os.ReadFile(workspacePath)
	if err != nil {
		return nil, errors.New("workspace not found")
	}
	var config struct {
		Folders []struct {
			Name string `json:"name"`
			Path string `json:"path"`
			URI  string `json:"uri"`
		} `json:"folders"`
	}
	if err := json.Unmarshal(data, &config); err != nil {
		return nil, errors.New("invalid workspace file")
	}
	roots := make([]workspaceRoot, 0, len(config.Folders))
	seen := make(map[string]bool)
	for _, folder := range config.Folders {
		path := folder.Path
		if path == "" && folder.URI != "" && !isRemoteURI(folder.URI) {
			path = uriToPath(folder.URI)
		}
		if path == "" {
			continue
		}
		if !filepath.IsAbs(path) {
			path = filepath.Join(filepath.Dir(workspacePath), path)
		}
		if resolved, err := filepath.EvalSymlinks(path); err == nil {
			path = resolved
		}
		path = filepath.Clean(path)
		if seen[strings.ToLower(path)] || !IsSafeFilePath(path) {
			continue
		}
		if folderInfo, err := os.Stat(path); err != nil || !folderInfo.IsDir() {
			continue
		}
		seen[strings.ToLower(path)] = true
		name := strings.TrimSpace(folder.Name)
		if name == "" {
			name = filepath.Base(path)
		}
		roots = append(roots, workspaceRoot{name: name, path: path, uri: normalizeURI(path)})
	}
	if len(roots) == 0 {
		return nil, errors.New("workspace has no accessible local folders")
	}
	return roots, nil
}
