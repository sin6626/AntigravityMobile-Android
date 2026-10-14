package com.antigravity.mobile.ui.chat

import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.QueuedMessageItem
import com.antigravity.mobile.data.model.QueuedAttachment
import org.junit.Assert.*
import org.junit.Test

class QueueSubmissionTest {
    @Test fun staleAndSameCaptionQueuesDoNotErasePendingImage() {
        val queued = QueuedMessageItem(id = "local:1", text = "same", media = listOf("image-one"))
        val submission = QueueSubmission(queued, OutgoingMessage("A",
            GatewayMessageItem(id = queued.id, text = queued.text, imageDataList = listOf(byteArrayOf(1))),
            setOf("old-step"), 5, true), setOf("old-queue"))
        assertFalse(submission.confirmedBy(emptyList(), emptyList()))
        assertFalse(submission.confirmedBy(listOf(queued.copy(id = "old-queue")), emptyList()))
        assertFalse(submission.confirmedBy(listOf(queued.copy(id = "new", media = listOf("another-image"))), emptyList()))
        assertTrue(submission.confirmedBy(listOf(queued.copy(id = "new")), emptyList()))
        assertTrue(submission.confirmedBy(listOf(queued.copy(id = "new", media = listOf("thumbnail"),
            attachments = listOf(QueuedAttachment(mimeType = "image/png", inlineData = "image-one")))), emptyList()))
        val consumed = GatewayMessageItem(id = "step", text = "same", stepIndex = 6, imageUrls = listOf("/image"))
        assertFalse(submission.confirmedBy(emptyList(), listOf(consumed.copy(id = "old-step"))))
        assertTrue(submission.confirmedBy(emptyList(), listOf(consumed)))
        assertFalse(submission.confirmedBy(emptyList(), listOf(consumed, consumed.copy(id = "ambiguous-step"))))
    }
}
