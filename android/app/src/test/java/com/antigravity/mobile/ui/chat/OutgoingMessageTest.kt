package com.antigravity.mobile.ui.chat

import com.antigravity.mobile.data.model.GatewayMessageItem
import com.antigravity.mobile.data.model.MessageAttachment
import org.junit.Assert.*
import org.junit.Test

class OutgoingMessageTest {
    @Test fun partialThumbnailSnapshotsReleaseOnlyTheReadyImageSlot() {
        val local = OutgoingMessage("A", GatewayMessageItem(id = "local:four", text = "four",
            imageDataList = List(4) { byteArrayOf((it + 1).toByte()) }), emptySet(), 0, true)
        val remote = local.message.copy(id = "server", stepIndex = 1, imageDataList = emptyList(), media = listOf("CQ=="))
        val partial = retainLocalImagePreviews(listOf(remote), emptyList(), listOf(local), "A").single()
        assertTrue(partial.imageDataList[0].isEmpty())
        for (index in 1..3) assertArrayEquals(local.message.imageDataList[index], partial.imageDataList[index])
        val invalid = retainLocalImagePreviews(listOf(remote.copy(media = listOf("**invalid**"))),
            emptyList(), listOf(local), "A").single()
        assertArrayEquals(local.message.imageDataList[0], invalid.imageDataList[0])
        val complete = remote.copy(media = listOf("CQ==", "Cg==", "Cw==", "DA=="))
        assertTrue(retainLocalImagePreviews(listOf(complete), listOf(partial), emptyList(), "A").single().imageDataList.isEmpty())
    }

    @Test fun aTextOnlyPendingMessageCannotConsumeAnImageReceiptWithTheSameCaption() {
        val image = OutgoingMessage("A", GatewayMessageItem(id = "local:image", text = "same",
            imageDataList = listOf(byteArrayOf(1)), attachments = listOf(MessageAttachment("image/png", "image-id"))), emptySet(), 0, true)
        val text = image.copy(message = GatewayMessageItem(id = "local:text", text = "same"), hasAttachments = false)
        val imageReceipt = image.message.copy(id = "image", stepIndex = 1, imageDataList = emptyList(), imageUrls = listOf("/image"))
        val textReceipt = text.message.copy(id = "text", stepIndex = 2)
        assertTrue(reconcileOutgoing(listOf(text, image), "A", listOf(imageReceipt, textReceipt)).isEmpty())
    }
    @Test fun identicalCaptionsConsumeEachLocalImageOnlyOnce() {
        val first = OutgoingMessage("chat", GatewayMessageItem(id = "local:1", text = "same",
            imageDataList = listOf(byteArrayOf(1)), attachments = listOf(MessageAttachment("image/png", "first-image"))), emptySet(), 4, true)
        val second = first.copy(message = first.message.copy(id = "local:2", imageDataList = listOf(byteArrayOf(2)),
            attachments = listOf(MessageAttachment("image/png", "second-image"))))
        val incoming = listOf(GatewayMessageItem(id = "first", text = "same", stepIndex = 5, imageUrls = listOf("/1"),
            attachments = first.message.attachments),
            GatewayMessageItem(id = "second", text = "same", stepIndex = 6, imageUrls = listOf("/2"), attachments = second.message.attachments))
        val merged = retainLocalImagePreviews(incoming, emptyList(), listOf(first, second), "chat")
        assertArrayEquals(byteArrayOf(1), merged[0].imageDataList.single())
        assertArrayEquals(byteArrayOf(2), merged[1].imageDataList.single())
        val withThumbnail = retainLocalImagePreviews(listOf(incoming[0].copy(media = listOf("AQID")), incoming[1]),
            emptyList(), listOf(first, second), "chat")
        assertTrue(withThumbnail[0].imageDataList.isEmpty())
        assertArrayEquals(byteArrayOf(2), withThumbnail[1].imageDataList.single())
        val reversed = retainLocalImagePreviews(incoming.reversed(), emptyList(), listOf(first, second), "chat")
        assertArrayEquals(byteArrayOf(2), reversed[0].imageDataList.single())
        assertArrayEquals(byteArrayOf(1), reversed[1].imageDataList.single())
        assertEquals(listOf(first.copy(knownUserIds = setOf("second"))),
            reconcileOutgoing(listOf(first, second), "chat", listOf(incoming[1])))
        val anonymous = incoming.map { it.copy(attachments = emptyList()) }
        assertTrue(retainLocalImagePreviews(anonymous, emptyList(), listOf(first, second), "chat").all { it.imageDataList.isEmpty() })
        assertEquals(listOf(first, second), reconcileOutgoing(listOf(first, second), "chat", anonymous))
    }
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
        assertTrue(retainLocalImagePreviews(listOf(confirmed.copy(media = listOf("AQID"))),
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
