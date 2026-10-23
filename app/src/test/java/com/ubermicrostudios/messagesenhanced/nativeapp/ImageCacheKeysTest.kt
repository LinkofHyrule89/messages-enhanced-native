package com.ubermicrostudios.messagesenhanced.nativeapp

import com.ubermicrostudios.messagesenhanced.nativeapp.util.ImageCacheKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageCacheKeysTest {

    @Test
    fun stripsAuthTokensAndKeepsAvatarIdentity() {
        val avatar = ImageCacheKeys.of(
            "https://messages.example/api/avatar?token=abc&phone=15555550100&source=sms&v=2",
        )
        assertEquals(
            "https://messages.example/api/avatar?phone=15555550100&source=sms&v=2",
            avatar,
        )
        assertEquals(
            "https://messages.example/api/media/m1",
            ImageCacheKeys.of("https://messages.example/api/media/m1?access_token=zzz"),
        )
        assertNull(ImageCacheKeys.of("/cache/pending/a.png"))
    }
}
