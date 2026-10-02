package com.antigravity.mobile.ui.demo

import com.antigravity.mobile.data.model.GatewayMessageItem
import org.commonmark.node.Heading
import org.commonmark.node.Paragraph
import org.junit.Assert.*
import org.junit.Test

class ConversationRenderItemsTest {
    @Test fun blocksKeepOrderIdentityAndReuseAcrossHistoryAndStreamingUpdates() {
        val user = GatewayMessageItem(id = "user", text = "question")
        val thought = GatewayMessageItem(id = "thought", type = "thought", text = "thinking")
        val reply = GatewayMessageItem(id = "reply", type = "agent", text = "# Heading\n\nFirst\n\nSecond")
        val items = buildConversationRenderItems(listOf(user, thought, reply), null)
        assertEquals(listOf("user", "thought", "reply:markdown:0", "reply:markdown:1", "reply"), items.map { it.key })
        assertTrue(items[2].node is Heading)
        assertTrue(items[3].node is Paragraph)
        assertNull(items[1].node)

        val older = GatewayMessageItem(id = "older", text = "older question")
        val paged = buildConversationRenderItems(listOf(older, user, thought, reply), null, items)
        assertSame(items[2], paged[3])
        assertSame(items[4], paged.last())

        val streaming = buildConversationRenderItems(listOf(user, reply), "reply", items)
        assertEquals(2, streaming.size)
        assertNull(streaming.last().node)
        assertTrue(streaming.last().streaming)
        val finished = buildConversationRenderItems(listOf(user, reply), null, streaming)
        assertEquals(streaming.last().key, finished.last().key)
        assertEquals(4, finished.size)
        assertEquals(finished.size, finished.map { it.key }.toSet().size)
    }
}
