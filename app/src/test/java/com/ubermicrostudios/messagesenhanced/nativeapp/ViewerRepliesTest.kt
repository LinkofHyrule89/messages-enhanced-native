package com.ubermicrostudios.messagesenhanced.nativeapp

import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.MessageDto
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ViewerGallery
import com.ubermicrostudios.messagesenhanced.nativeapp.util.ViewerReplies
import com.ubermicrostudios.messagesenhanced.nativeapp.util.formatPhoneForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerRepliesTest {

    private fun message(
        id: String,
        replyTo: String = "",
        body: String = "",
        at: Long = 0L,
        fromMe: Boolean = false,
        sender: String = "",
        mime: String = "",
        mediaId: String = "",
        local: String = "",
    ) = MessageDto(
        messageId = id,
        replyToId = replyTo,
        body = body,
        timestampMs = at,
        isFromMe = fromMe,
        senderName = sender,
        mimeType = mime,
        mediaId = mediaId,
        localPreview = local,
    )

    @Test
    fun directReplies_countOnlyMessagesQuotingThatImage() {
        val image = message(id = "img", mime = "image/jpeg", mediaId = "img", at = 1)
        val older = message(id = "c", replyTo = "img", body = "first", at = 10, sender = "Ada")
        val newer = message(id = "a", replyTo = "img", body = "second", at = 20, fromMe = true)
        val other = message(id = "b", replyTo = "other", body = "nope", at = 15)
        val self = message(id = "img", replyTo = "img", body = "not a reply", at = 30)
        val messages = listOf(newer, other, image, self, older)

        val replies = ViewerReplies.directReplies(messages, "img")
        assertEquals(listOf("c", "a"), replies.map { it.messageId })
        assertEquals(2, ViewerReplies.count(messages, "img"))
        assertEquals("2", ViewerReplies.badgeLabel(2))
        assertEquals(0, ViewerReplies.count(messages, "missing"))
        assertEquals(0, ViewerReplies.count(messages, ""))
        assertNull(ViewerReplies.badgeLabel(0))
        assertEquals("99+", ViewerReplies.badgeLabel(100))
    }

    @Test
    fun senderLabel_youOrFormattedName() {
        val mine = message(id = "m", fromMe = true, sender = "Pat")
        assertEquals("You", ViewerReplies.senderLabel(mine, "Pat", isGroup = false))
        val named = message(id = "t", sender = "Ada")
        assertEquals("Ada", ViewerReplies.senderLabel(named, "Ada", isGroup = true))
        val number = message(id = "n", sender = "+18015551234")
        assertEquals(
            formatPhoneForDisplay("+18015551234"),
            ViewerReplies.senderLabel(number, "Chat", isGroup = true),
        )
    }

    @Test
    fun mergeRemote_keepsAPendingCommentUntilTheServerCopyArrives() {
        val image = message(id = "img", at = 1)
        val pending = message(id = "local-comment-9", replyTo = "img", body = "nice", at = 50, fromMe = true)
        val localPhoto = message(id = "local-photo", mime = "image/jpeg", at = 40, local = "/cache/a.jpg")
        val remote = listOf(image, message(id = "old", body = "hi", at = 2))
        val merged = ViewerReplies.mergeRemote(listOf(image, localPhoto, pending), remote)
        assertEquals(listOf("img", "old", "local-photo", "local-comment-9"), merged.map { it.messageId })

        val landed = message(id = "server-1", replyTo = "img", body = "nice", at = 55, fromMe = true)
        val after = ViewerReplies.mergeRemote(merged, remote + landed, pending)
        assertEquals(false, after.any { it.messageId == "local-comment-9" })
        assertEquals(true, after.any { it.messageId == "server-1" })
    }

    @Test
    fun gallery_ordersOpenableImagesAndStartsAtTheTappedOne() {
        val text = message(id = "t", body = "hi", at = 1)
        val first = message(id = "a", mime = "image/jpeg", at = 2, local = "/cache/a.jpg")
        val video = message(id = "v", mime = "video/mp4", mediaId = "v", at = 3)
        val second = message(id = "b", mime = "image/png", at = 4)
        val messages = listOf(second, text, video, first)
        val url = { message: MessageDto -> "https://messages.example/api/media/${message.messageId}" }
        val images = ViewerGallery.images(messages, url)
        assertEquals(listOf("a", "b"), images.map { it.messageId })
        assertEquals(1, ViewerGallery.startIndex(images, "b"))
        assertEquals(0, ViewerGallery.startIndex(images, "missing"))
    }
}
