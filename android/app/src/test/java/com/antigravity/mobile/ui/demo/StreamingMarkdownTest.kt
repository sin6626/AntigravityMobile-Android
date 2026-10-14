package com.antigravity.mobile.ui.demo

import com.antigravity.mobile.data.model.GatewayMessageItem
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.renderer.html.HtmlRenderer
import org.junit.Assert.*
import org.junit.Test

class StreamingMarkdownTest {
    private fun render(text: String, previous: List<ConversationRenderItem> = emptyList(), running: Boolean = true) =
        buildConversationRenderItems(listOf(GatewayMessageItem(id = "live", type = "agent", text = text)),
            if (running) "live" else null, previous, running)

    @Test fun formatsBeforeCompletionAndKeepsBlockKeysWhenAppendingAndFinishing() {
        val first = render("# 标题\n\n第一段 **加粗**")
        assertTrue(first.first().node is Heading)
        assertTrue(first.all { it.streaming })
        assertFalse(first.any { it.canCopy })
        val source = "# 标题\n\n第一段 **加粗**\n\n第二段"
        val next = render(source, first)
        assertEquals(first.map { it.key }, next.take(first.size).map { it.key })
        val finished = render(source, next, false)
        assertEquals(next.map { it.key }, finished.map { it.key })
        assertEquals(1, finished.count { it.canCopy })
        assertTrue(finished.last().canCopy)
        assertEquals(source, finished.last().message.effectiveText)
    }

    @Test fun prefixesCorrectionsAndReferenceDefinitionsMatchFinalParsing() {
        val snapshots = listOf("# 标题\n\n正文 **加", "# 标题\n\n正文 **加粗**\n\n```kotlin\nval a = 1",
            "# 标题\n\n```kotlin\nval a = 2\n```\n\n- 第一项\n- 第二项",
            "| A | B |\n|---|---|\n| 1 | 2 |\n\n[引用][target]",
            "| A | B |\n|---|---|\n| 1 | 2 |\n\n[引用][target]\n\n[target]: https://example.com",
            "缩短", "等长", "")
        val html = HtmlRenderer.builder().build()
        var previous = emptyList<ConversationRenderItem>()
        snapshots.forEach { text ->
            val current = render(text, previous)
            assertEquals(render(text, running = false).map { it.node?.let(html::render) },
                current.map { it.node?.let(html::render) })
            assertEquals(current.size, current.map { it.key }.distinct().size)
            previous = current
        }
    }

    @Test fun parserIdentifiesOpenAndClosedDiagramFences() {
        val open = render("```mermaid\ngraph TD\nA-->B").single().node as FencedCodeBlock
        assertNull(open.closingFenceLength)
        val closed = render("```mermaid\ngraph TD\nA-->B\n```").single().node as FencedCodeBlock
        assertNotNull(closed.closingFenceLength)
    }

    @Test fun unchangedSnapshotsReusePreparedItems() {
        val first = render("# 标题\n\n正文")
        val next = render("# 标题\n\n正文", first)
        first.zip(next).forEach { (a, b) -> assertSame(a, b) }
    }
}
