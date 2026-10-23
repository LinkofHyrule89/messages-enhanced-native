package com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.JsonConfig
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.StoredCookie
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext

    private val settings: SharedPreferences =
        appContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    private val lock = Any()
    private val writer = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var lastSelectedLoaded = false
    private var lastSelectedId = ""

    private val secure: SharedPreferences by lazy {
        val key = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var serverUrl: String
        get() = settings.getString(KEY_SERVER_URL, null).orEmpty().trim().trimEnd('/')
        set(value) = settings.edit { putString(KEY_SERVER_URL, value.trim().trimEnd('/')) }

    var lastUpdateCheckAt: Long
        get() = settings.getLong(KEY_UPDATE_CHECK_AT, 0L)
        set(value) = settings.edit { putLong(KEY_UPDATE_CHECK_AT, value) }

    /** Off until the user turns it on in Settings. */
    var autoUpdate: Boolean
        get() = settings.getBoolean(KEY_AUTO_UPDATE, false)
        set(value) = settings.edit { putBoolean(KEY_AUTO_UPDATE, value) }

    /**
     * Written with commit before the install session is committed. The update
     * kills the process, so apply() could be lost before it reaches disk.
     */
    var relaunchAfterUpdate: Boolean
        get() = readRelaunchAfterUpdate(appContext)
        set(value) = writeRelaunchAfterUpdate(appContext, value)

    var notificationsEnabled: Boolean
        get() = settings.getBoolean(KEY_NOTIFICATIONS, false)
        set(value) = settings.edit { putBoolean(KEY_NOTIFICATIONS, value) }

    var hideMessageText: Boolean
        get() = settings.getBoolean(KEY_HIDE_TEXT, false)
        set(value) = settings.edit { putBoolean(KEY_HIDE_TEXT, value) }

    fun loadCookies(): List<StoredCookie> {
        val raw = runCatching { secure.getString(KEY_COOKIES, null) }.getOrNull() ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            JsonConfig.json.decodeFromString<List<StoredCookie>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveCookies(cookies: List<StoredCookie>) {
        val raw = JsonConfig.json.encodeToString(cookies)
        secure.edit { putString(KEY_COOKIES, raw) }
    }

    fun clearCookies() {
        secure.edit { remove(KEY_COOKIES) }
    }

    /**
     * Conversation last opened in the list. The disk write runs off the main thread.
     * Tablet cold start reads this to put that chat back in the split pane.
     */
    fun lastSelectedConversationId(): String = synchronized(lock) {
        ensureLastSelectedLocked()
        lastSelectedId
    }

    fun setLastSelectedConversationId(conversationId: String?) {
        val value = conversationId?.trim().orEmpty()
        synchronized(lock) {
            lastSelectedId = value
            lastSelectedLoaded = true
        }
        enqueueLastSelectedWrite()
    }

    /** Drops the saved id when that conversation is archived or moved to trash. */
    fun clearLastSelectedConversationId(conversationId: String) {
        val id = conversationId.trim()
        if (id.isBlank()) return
        synchronized(lock) {
            ensureLastSelectedLocked()
            if (lastSelectedId != id) return
            lastSelectedId = ""
        }
        enqueueLastSelectedWrite()
    }

    private fun ensureLastSelectedLocked() {
        if (lastSelectedLoaded) return
        lastSelectedId = settings.getString(KEY_LAST_CONVERSATION, null).orEmpty()
        lastSelectedLoaded = true
    }

    private fun enqueueLastSelectedWrite() {
        writer.launch {
            val value = synchronized(lock) { lastSelectedId }
            runCatching {
                settings.edit(commit = true) {
                    if (value.isBlank()) remove(KEY_LAST_CONVERSATION) else putString(KEY_LAST_CONVERSATION, value)
                }
            }
        }
    }

    companion object {
        internal const val SETTINGS_FILE = "me_native_settings"
        private const val SECURE_FILE = "me_native_secure"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_HIDE_TEXT = "hide_message_text"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_UPDATE_CHECK_AT = "update_check_at"
        internal const val KEY_AUTO_UPDATE = "auto_update"
        internal const val KEY_RELAUNCH_AFTER_UPDATE = "relaunch_after_update"
        private const val KEY_LAST_CONVERSATION = "last_selected_conversation_id"

        fun readRelaunchAfterUpdate(context: Context): Boolean {
            return context.applicationContext
                .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
                .getBoolean(KEY_RELAUNCH_AFTER_UPDATE, false)
        }

        fun writeRelaunchAfterUpdate(context: Context, enabled: Boolean) {
            context.applicationContext
                .getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
                .edit(commit = true) { putBoolean(KEY_RELAUNCH_AFTER_UPDATE, enabled) }
        }
    }
}
