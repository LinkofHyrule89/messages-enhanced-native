package com.ubermicrostudios.messagesenhanced.nativeapp.data.net

import com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaseUrlProvider @Inject constructor(
    private val prefs: AppPreferences,
) {
    fun origin(): String = prefs.serverUrl.trim().trimEnd('/')

    fun mediaUrl(messageId: String): String =
        origin() + "/api/media/" + messageId
}
