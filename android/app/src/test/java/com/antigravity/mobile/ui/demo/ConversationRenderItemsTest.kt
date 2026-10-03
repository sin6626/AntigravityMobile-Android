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
        assertEquals(listOf("user", "process:user", "reply:markdown:0", "reply:markdown:1", "reply"), items.map { it.key })
        assertTrue(items[2].node is Heading)
        assertTrue(items[3].node is Paragraph)
        assertNull(items[1].node)
        assertEquals(listOf(thought), items[1].process)
        assertEquals(listOf("reply"), items.filter { it.canCopy }.map { it.message.id })

        val older = GatewayMessageItem(id = "older", text = "older question")
        val paged = buildConversationRenderItems(listOf(older, user, thought, reply), null, items)
        assertSame(items[2], paged[3])
        assertSame(items[4], paged.last())

        val streaming = buildConversationRenderItems(listOf(user, reply), "reply", items)
        assertEquals(2, streaming.size)
        assertNull(streaming.last().node)
        assertTrue(streaming.last().streaming)
        assertFalse(streaming.any { it.canCopy })
        val finished = buildConversationRenderItems(listOf(user, reply), null, streaming)
        assertEquals(streaming.last().key, finished.last().key)
        assertEquals(4, finished.size)
        assertEquals(1, finished.count { it.canCopy })
        assertEquals(finished.size, finished.map { it.key }.toSet().size)
    }

    @Test fun codingTurnsKeepAllStepsInOrderAndOnlyExposeTheLatestAnswer() {
        fun msg(id: String, type: String) = GatewayMessageItem(id = id, type = type, text = "# $id\n\nbody")
        val user = msg("u", "user")
        val thought = msg("t", "thought")
        val tool = msg("tool", "tools").copy(toolCount = 2, details = listOf(
            com.antigravity.mobile.data.model.GatewayStepDetail(name = "read", status = "CORTEX_STEP_STATUS_DONE")))
        val progress = msg("progress", "agent")
        val nextThought = msg("t2", "thought")
        val final = msg("final", "agent")
        val source = listOf(user, thought, tool, progress, nextThought, final)
        val result = buildConversationRenderItems(source, null)
        val process = result.single { it.process.isNotEmpty() }
        assertEquals(listOf(thought, tool, progress, nextThought), process.process)
        assertEquals(process.process.map { it.id }, process.executionItems.filter { it.heading }.map { it.message.id })
        assertTrue(process.executionItems.any { it.detail?.name == "read" })
        assertEquals(listOf("final"), result.filter { it.canCopy }.map { it.message.id })
        assertEquals(result.size, result.map { it.key }.distinct().size)

        val live = buildConversationRenderItems(source.dropLast(1), "progress", isRunning = true)
        assertTrue(live.single { it.process.isNotEmpty() }.processRunning)
        assertEquals(source.drop(1).dropLast(1), live.single { it.process.isNotEmpty() }.process)
        assertFalse(live.any { it.canCopy })
        val appended = buildConversationRenderItems(source, "final", live, true)
        assertEquals(process.key, appended.single { it.process.isNotEmpty() }.key)
        assertTrue(appended.last().streaming)

        val partial = buildConversationRenderItems(source.drop(2), null)
        val paged = buildConversationRenderItems(source, null, partial)
        assertEquals(partial.single { it.process.isNotEmpty() }.key, paged.single { it.process.isNotEmpty() }.key)
        assertEquals(result.single { it.process.isNotEmpty() }.process, paged.single { it.process.isNotEmpty() }.process)
        assertSame(partial.last(), paged.last())
        val second = buildConversationRenderItems(source + listOf(msg("u2", "user"), msg("t3", "thought"), msg("final2", "agent")), null)
        assertEquals(listOf("process:u", "process:u2"), second.filter { it.process.isNotEmpty() }.map { it.key })
    }
}
