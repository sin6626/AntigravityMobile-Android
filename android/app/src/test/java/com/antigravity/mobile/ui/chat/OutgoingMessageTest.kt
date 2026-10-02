package com.antigravity.mobile.ui.chat

import com.antigravity.mobile.data.model.GatewayMessageItem
import org.junit.Assert.*
import org.junit.Test

class OutgoingMessageTest {
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
    }
}
