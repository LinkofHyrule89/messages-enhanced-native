package com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.JsonConfig
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.StoredCookie
import dagger.hilt.android.qualifiers.ApplicationContext
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

    companion object {
        private const val SETTINGS_FILE = "me_native_settings"
        private const val SECURE_FILE = "me_native_secure"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_HIDE_TEXT = "hide_message_text"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_UPDATE_CHECK_AT = "update_check_at"
    }
}
