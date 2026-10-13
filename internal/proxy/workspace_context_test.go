package proxy

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"testing"
)

func TestHandleWorkspaceContextSearchAndPagination(t *testing.T) {
	root := t.TempDir()
	t.Setenv("MULTIGRAVITY_ALLOWED_WORKSPACE_ROOTS", root)
	resetWorkspaceRootsForTest()
	defer resetWorkspaceRootsForTest()

	mustWriteWorkspaceFile(t, filepath.Join(root, "alpha-file.kt"), "alpha")
	mustWriteWorkspaceFile(t, filepath.Join(root, "beta-file.kt"), "beta")
	mustWriteWorkspaceFile(t, filepath.Join(root, "src", "nested-file.kt"), "nested")
	mustWriteWorkspaceFile(t, filepath.Join(root, "node_modules", "hidden-file.js"), "hidden")
	mustWriteWorkspaceFile(t, filepath.Join(root, ".env"), "secret")

	endpoint := "/gateway/workspace/context?workspaceUri=" + url.QueryEscape(normalizeURI(root)) + "&query=file&limit=2"
	p := &Proxy{}
	first := httptest.NewRecorder()
	p.ServeHTTP(first, httptest.NewRequest(http.MethodGet, endpoint, nil))
	if first.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", first.Code, first.Body.String())
	}
	var page WorkspaceContextResponse
	if err := json.NewDecoder(first.Body).Decode(&page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 2 || page.NextCursor == "" {
		t.Fatalf("expected two items and a cursor, got %+v", page)
	}
	for _, item := range page.Items {
		if item.Type != "file" || item.ScopeItem.File == nil || item.ScopeItem.File.AbsoluteURI != item.URI {
			t.Fatalf("invalid file scope item: %+v", item)
		}
		if item.ScopeItem.File.WorkspaceURIsToRelativePaths[item.RootURI] != item.RelativePath {
			t.Fatalf("workspace relative path changed: %+v", item)
		}
	}

	second := httptest.NewRecorder()
	p.ServeHTTP(second, httptest.NewRequest(http.MethodGet, endpoint+"&cursor="+url.QueryEscape(page.NextCursor), nil))
	if second.Code != http.StatusOK {
		t.Fatalf("expected second page 200, got %d: %s", second.Code, second.Body.String())
	}
	var secondPage WorkspaceContextResponse
	_ = json.NewDecoder(second.Body).Decode(&secondPage)
	if len(secondPage.Items) != 1 || secondPage.Items[0].RelativePath != "src/nested-file.kt" {
		t.Fatalf("unexpected second page: %+v", secondPage)
	}
	for _, item := range append(page.Items, secondPage.Items...) {
		if item.RelativePath == ".env" || item.RelativePath == "node_modules/hidden-file.js" {
			t.Fatalf("restricted or ignored file was exposed: %+v", item)
		}
	}
}

func TestWorkspaceContextResolvesCodeWorkspaceFolders(t *testing.T) {
	parent := t.TempDir()
	t.Setenv("MULTIGRAVITY_ALLOWED_WORKSPACE_ROOTS", parent)
	resetWorkspaceRootsForTest()
	defer resetWorkspaceRootsForTest()

	mustWriteWorkspaceFile(t, filepath.Join(parent, "client", "shared-name.kt"), "client")
	mustWriteWorkspaceFile(t, filepath.Join(parent, "server", "shared-name.go"), "server")
	workspace := filepath.Join(parent, "mobile.code-workspace")
	mustWriteWorkspaceFile(t, workspace, `{"folders":[{"name":"客户端","path":"client"},{"name":"服务端","path":"server"}]}`)

	items, truncated, err := searchWorkspaceContext(normalizeURI(workspace), "shared-name")
	if err != nil {
		t.Fatal(err)
	}
	if truncated || len(items) != 2 || items[0].RootName != "客户端" || items[1].RootName != "服务端" {
		t.Fatalf("workspace roots were not preserved: %+v, truncated=%v", items, truncated)
	}
}

func mustWriteWorkspaceFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}
