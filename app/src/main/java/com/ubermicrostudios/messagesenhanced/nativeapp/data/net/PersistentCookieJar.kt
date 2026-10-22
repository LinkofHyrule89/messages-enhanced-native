package com.ubermicrostudios.messagesenhanced.nativeapp.data.net

import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.StoredCookie
import com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs.AppPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentCookieJar @Inject constructor(
    private val prefs: AppPreferences,
) : CookieJar {

    @Volatile
    private var cache: MutableList<StoredCookie> = prefs.loadCookies().toMutableList()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        cache.removeAll { it.expiresAt <= now }
        for (cookie in cookies) {
            cache.removeAll { sameIdentity(it, cookie) }
            if (cookie.expiresAt > now) {
                cache.add(cookie.toStored())
            }
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = cache.removeAll { it.expiresAt <= now }
        if (expired) persist()
        return cache.mapNotNull { it.toOkHttp() }.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        prefs.clearCookies()
    }

    @Synchronized
    fun hasSessionCookie(): Boolean {
        val now = System.currentTimeMillis()
        return cache.any { it.name == SESSION_COOKIE && it.expiresAt > now && it.value.isNotBlank() }
    }

    private fun persist() {
        prefs.saveCookies(cache.toList())
    }

    private fun sameIdentity(stored: StoredCookie, cookie: Cookie): Boolean =
        stored.name == cookie.name &&
            stored.domain.equals(cookie.domain, ignoreCase = true) &&
            stored.path == cookie.path

    private fun Cookie.toStored(): StoredCookie = StoredCookie(
        name = name,
        value = value,
        expiresAt = expiresAt,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
        persistent = persistent,
    )

    private fun StoredCookie.toOkHttp(): Cookie? {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        try {
            if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        const val SESSION_COOKIE = "tm_session"
    }
}
