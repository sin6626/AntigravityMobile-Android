package com.antigravity.mobile.ui.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCommentSelectionTest {
    @Test
    fun `markdown block maps to desktop text selection line contract`() {
        val source = "# 开发方案\n\n第一行\n第二行\n\n结尾"
        val blocks = parseMarkdownBlocks(source)

        val heading = requireNotNull(planTextSelection(source, blocks[0]))
        assertEquals(0, heading.startLine)
        assertEquals(1, heading.endLine)
        assertEquals("# 开发方案", heading.content)

        val paragraph = requireNotNull(planTextSelection(source, blocks[1]))
        assertEquals(2, paragraph.startLine)
        assertEquals(4, paragraph.endLine)
        assertEquals("第一行\n第二行", paragraph.content)
    }
}
