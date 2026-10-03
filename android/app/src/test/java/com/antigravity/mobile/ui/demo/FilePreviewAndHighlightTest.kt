package com.antigravity.mobile.ui.demo

import me.rerere.highlight.CodeHighlighter
import me.rerere.highlight.HighlightToken
import org.junit.Assert.*
import org.junit.Test

class FilePreviewAndHighlightTest {
    @Test fun routesEncodedFileLinksAndResolvesDocumentRelativePaths() {
        assertEquals(FilePreviewKind.MARKDOWN, filePreviewKind("file:///h:/Project/Agent.MD#L12"))
        assertEquals(FilePreviewKind.IMAGE, filePreviewKind("file:///h:/Project/%E6%88%AA%E5%9B%BE.PNG"))
        assertEquals("截图.PNG", previewFileName("file:///h:/Project/%E6%88%AA%E5%9B%BE.PNG"))
        assertEquals(FilePreviewKind.CODE, filePreviewKind("file:///h:/Project/MainActivity.kt"))
        assertEquals(FilePreviewKind.UNSUPPORTED, filePreviewKind("file:///h:/Project/sample.pdf"))
        assertEquals(FilePreviewKind.TEXT, filePreviewKind("file:///h:/Project/notes.txt"))
        assertEquals("file:///h:/Project/screenshots/a%20b.png", resolveDocumentLink("file:///h:/Project/Agent.md", "screenshots/a b.png"))
        assertEquals("file:///h:/Main.kt#L5", resolveDocumentLink("file:///h:/Project/Agent.md", "../Main.kt#L5"))
    }

    @Test fun longNativeHighlightKeepsUnicodeWhitespaceAndUsesMultipleColors() {
        val code = ("// 中文注释 🙂\nval message = \"保留 空格\"\n".repeat(160)) + "\tprintln(message)\n"
        assertTrue(code.length > 4096)
        val tokens = CodeHighlighter().highlight(code, "kotlin")
        assertTrue(tokens.any { it is HighlightToken.Styled && it.type == "keyword" })
        val styled = colorizeCode(tokens)
        assertEquals(code, styled.text)
        assertTrue(styled.spanStyles.map { it.item.color }.distinct().size >= 3)
        assertEquals("?? not code \n", colorizeCode(CodeHighlighter().highlight("?? not code \n", "unknown-language")).text)
    }
}
