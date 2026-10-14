package com.antigravity.mobile.ui.chat

import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.MessageAttachment
import org.junit.Assert.*
import org.junit.Test

class OutgoingMessageTest {
    @Test fun confirmedImagesKeepLocalPreviewAcrossSlowMediaAndRepeatedSnapshots() {
        val image = byteArrayOf(1, 2, 3)
        val local = OutgoingMessage("chat", GatewayMessageItem(id = "local:image", text = "same",
            imageDataList = listOf(image)), setOf("old"), 4, true)
        val old = GatewayMessageItem(id = "old", text = "same", stepIndex = 4, imageUrls = listOf("/old.png"))
        val textOnly = GatewayMessageItem(id = "new", text = "same", stepIndex = 5)
        assertTrue(retainLocalImagePreviews(listOf(old, textOnly), emptyList(), listOf(local), "chat")
            .all { it.imageDataList.isEmpty() })
        val confirmed = textOnly.copy(imageUrls = listOf("/not-ready.png"))
        val rendered = retainLocalImagePreviews(listOf(confirmed), emptyList(), listOf(local), "chat")
        assertArrayEquals(image, rendered.single().imageDataList.single())
        assertTrue(reconcileOutgoing(listOf(local), "chat", rendered).isEmpty())
        assertArrayEquals(image, retainLocalImagePreviews(listOf(confirmed), rendered, emptyList(), "chat")
            .single().imageDataList.single())
        assertTrue(retainLocalImagePreviews(listOf(confirmed), emptyList(), listOf(local), "other")
            .single().imageDataList.isEmpty())
        assertTrue(retainLocalImagePreviews(listOf(confirmed.copy(media = listOf("thumbnail"))),
            rendered, emptyList(), "chat").single().imageDataList.isEmpty())
    }
    @Test fun onlyNewUserStepsConfirmPendingAndRepeatedSnapshotsCannotConsumeTheNextIdenticalSend() {
        val old = GatewayMessageItem(id = "old", text = "same", stepIndex = 4)
        val first = OutgoingMessage("chat", GatewayMessageItem(id = "local:1", text = "same"), setOf("old"), 4)
        val second = first.copy(message = first.message.copy(id = "local:2"))
        val other = first.copy(conversationId = "other")
        val image = first.copy(message = first.message.copy(id = "local:image", imageDataList = listOf(byteArrayOf(1))))
        val unseenOlder = old.copy(id = "older", stepIndex = 2)
        val confirmed = old.copy(id = "new", stepIndex = 5)
        val pending = listOf(first, second, image, other)
        assertEquals(4, reconcileOutgoing(pending, "chat", listOf(old, unseenOlder)).size)
        val remaining = reconcileOutgoing(pending, "chat", listOf(old, confirmed))
        assertEquals(listOf("local:2", "local:image", "local:1"), remaining.map { it.message.id })
        assertEquals(remaining, reconcileOutgoing(remaining, "chat", listOf(old, confirmed)))
        val next = confirmed.copy(id = "next", stepIndex = 6)
        val withImage = next.copy(id = "image", stepIndex = 7, imageUrls = listOf("/image.png"))
        assertEquals(listOf(other), reconcileOutgoing(remaining, "chat", listOf(old, confirmed, next, withImage)))
        val waitingImage = reconcileOutgoing(listOf(image), "chat", listOf(confirmed))
        assertEquals(listOf(image), waitingImage)
        assertTrue(reconcileOutgoing(waitingImage, "chat", listOf(confirmed.copy(imageUrls = listOf("/image.png")))).isEmpty())

        val file = first.copy(
            message = first.message.copy(id = "local:file", attachments = listOf(MessageAttachment("text/plain", "notes.txt"))),
            hasAttachments = true,
        )
        assertEquals(listOf(file), reconcileOutgoing(listOf(file), "chat", listOf(confirmed)))
        assertTrue(reconcileOutgoing(listOf(file), "chat", listOf(confirmed.copy(
            attachments = listOf(MessageAttachment("text/plain", "notes.txt"))))).isEmpty())
    }
}
