package com.ubermicrostudios.messagesenhanced.nativeapp.data.repo

import com.ubermicrostudios.messagesenhanced.nativeapp.data.net.BaseUrlProvider
import com.ubermicrostudios.messagesenhanced.nativeapp.data.net.PersistentCookieJar
import com.ubermicrostudios.messagesenhanced.nativeapp.data.net.SseClient
import com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

sealed class LoginResult {
    data object Success : LoginResult()
    data object WrongSecret : LoginResult()
    data object RateLimited : LoginResult()
    data class Failed(val message: String) : LoginResult()
}

@Singleton
class AuthRepository @Inject constructor(
    private val client: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val prefs: AppPreferences,
    private val baseUrl: BaseUrlProvider,
    private val sse: SseClient,
) {
    fun hasSession(): Boolean = prefs.serverUrl.isNotBlank() && cookieJar.hasSessionCookie()

    fun serverUrl(): String = prefs.serverUrl

    fun saveServerUrl(url: String) {
        prefs.serverUrl = url.trim().trimEnd('/')
    }

    suspend fun login(secret: String): LoginResult = withContext(Dispatchers.IO) {
        val origin = baseUrl.origin()
        val body = FormBody.Builder().add("secret", secret).build()
        val request = Request.Builder()
            .url("$origin/login")
            .post(body)
            .removeHeader("Origin")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val location = resp.header("Location").orEmpty()
                val hasSession = cookieJar.hasSessionCookie()
                when {
                    resp.code == 401 -> LoginResult.WrongSecret
                    resp.code == 429 -> LoginResult.RateLimited
                    resp.code == 303 && hasSession -> LoginResult.Success
                    hasSession -> LoginResult.Success
                    resp.code in 300..399 && !location.contains("/login") -> LoginResult.Success
                    else -> LoginResult.Failed("Login failed (HTTP ${resp.code}).")
                }
            }
        } catch (e: Exception) {
            LoginResult.Failed("Could not reach $origin.")
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        sse.stop()
        val origin = baseUrl.origin()
        val request = Request.Builder()
            .url("$origin/logout")
            .get()
            .removeHeader("Origin")
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {
        }
        cookieJar.clear()
    }
}
