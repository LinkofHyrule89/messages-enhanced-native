package com.ubermicrostudios.messagesenhanced.nativeapp.data.repo

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.PushSettingsRequest
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.PushSubscribeRequest
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.PushTestRequest
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.PushUnsubscribeRequest
import com.ubermicrostudios.messagesenhanced.nativeapp.data.net.MessagesApi
import com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs.AppPreferences
import com.ubermicrostudios.messagesenhanced.nativeapp.push.FcmConfig
import com.ubermicrostudios.messagesenhanced.nativeapp.push.Push
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: MessagesApi,
    private val prefs: AppPreferences,
) {
    fun notificationsEnabled(): Boolean = prefs.notificationsEnabled
    fun hideMessageText(): Boolean = prefs.hideMessageText

    suspend fun subscribe(hideText: Boolean = prefs.hideMessageText): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val config = api.pushConfig()
            if (!config.available || config.fcm == null) {
                error("Push is not available on this server.")
            }
            val fcm = FcmConfig.from(config.fcm) ?: error("Server FCM config is incomplete.")
            Push.saveConfig(context, fcm)
            Push.init(context, fcm)
            val token = FirebaseMessaging.getInstance().token.await()
            Push.saveToken(context, token, markChanged = false)
            val endpoint = Push.fcmEndpoint(token)
            val resp = api.pushSubscribe(
                PushSubscribeRequest(
                    endpoint = endpoint,
                    label = "Messages Enhanced Native",
                    hideText = hideText,
                ),
            )
            if (!resp.isSuccessful) error("Subscribe failed (HTTP ${resp.code()}).")
            prefs.notificationsEnabled = true
            prefs.hideMessageText = hideText
        }
    }

    suspend fun unsubscribe(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = Push.savedToken(context)
            if (token.isNotBlank()) {
                runCatching {
                    api.pushUnsubscribe(PushUnsubscribeRequest(Push.fcmEndpoint(token)))
                }
            }
            Push.clearToken(context)
            prefs.notificationsEnabled = false
        }
    }

    suspend fun resubscribeIfEnabled(token: String) {
        if (!prefs.notificationsEnabled) {
            Push.saveToken(context, token, markChanged = true)
            return
        }
        Push.saveToken(context, token, markChanged = false)
        runCatching {
            api.pushSubscribe(
                PushSubscribeRequest(
                    endpoint = Push.fcmEndpoint(token),
                    label = "Messages Enhanced Native",
                    hideText = prefs.hideMessageText,
                ),
            )
        }
    }

    suspend fun setHideText(hide: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = Push.savedToken(context)
            if (token.isBlank()) error("Not subscribed.")
            val resp = api.pushSettings(
                PushSettingsRequest(endpoint = Push.fcmEndpoint(token), hideText = hide),
            )
            if (!resp.isSuccessful) error("Could not update hide-text (HTTP ${resp.code()}).")
            prefs.hideMessageText = hide
        }
    }

    suspend fun sendTest(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = Push.savedToken(context)
            if (token.isBlank()) error("Not subscribed.")
            val resp = api.pushTest(PushTestRequest(Push.fcmEndpoint(token)))
            if (!resp.isSuccessful) error("Test failed (HTTP ${resp.code()}).")
        }
    }
}
