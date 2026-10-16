package com.antigravity.mobile.ui.demo

import com.antigravity.mobile.data.model.ConversationDiffFile
import com.antigravity.mobile.data.model.ConversationDiffResponse
import org.junit.Assert.*
import org.junit.Test

class ConversationDiffSummaryTest {
    @Test fun countsOnlyChangesAndKeepsUnavailableFilesVisible() {
        val review = ConversationDiffResponse(files = listOf(
            ConversationDiffFile(originalContent = "same\nold\nend", modifiedContent = "same\nnew\nend"),
            ConversationDiffFile(originalContent = "", modifiedContent = "one\ntwo"),
            ConversationDiffFile(originalContent = "gone", modifiedContent = ""),
        ), unavailableUris = listOf("file:///missing.kt"))
        assertEquals(ConversationDiffSummary(3, 3, 2, 1), summarizeConversationDiff(review))
        assertNull(summarizeConversationDiff(ConversationDiffResponse()))
        assertEquals(ConversationDiffSummary(0, 0, 0, 1),
            summarizeConversationDiff(ConversationDiffResponse(unavailableUris = listOf("file:///missing.kt"))))
    }
}
