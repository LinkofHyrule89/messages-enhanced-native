package com.ubermicrostudios.messagesenhanced.nativeapp.util

import android.net.Uri
import coil.key.Keyer
import coil.request.ImageRequest
import coil.request.Options
import java.net.URI
import java.net.URLDecoder

/**
 * Stable Coil keys for authenticated media.
 * `/api/avatar` is identified by its query, so only auth-token parameters are removed.
 * A rotating `token` must not miss the disk cache, and two people must not share one entry.
 */
object ImageCacheKeys {
    private val AUTH_PARAMS = setOf(
        "token",
        "access_token",
        "auth",
        "auth_token",
        "authorization",
        "sig",
        "signature",
        "session",
        "expires",
        "exp",
        "api_key",
        "apikey",
    )

    fun of(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host ?: return null
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val kept = stableQuery(uri.rawQuery)
        val port = uri.port
        val root = buildString {
            append(scheme)
            append("://")
            append(host)
            if (port != -1 && !((scheme == "https" && port == 443) || (scheme == "http" && port == 80))) {
                append(':')
                append(port)
            }
            append(path)
        }
        return if (kept.isEmpty()) root else "$root?$kept"
    }

    private fun stableQuery(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val name = URLDecoder.decode(part.substringBefore('='), Charsets.UTF_8.name())
                if (name.lowercase() in AUTH_PARAMS) null else part
            }
            .sortedBy { it.substringBefore('=').lowercase() }
            .joinToString("&")
    }
}

fun ImageRequest.Builder.stableCacheKey(data: Any?): ImageRequest.Builder {
    val key = (data as? String)?.let(ImageCacheKeys::of) ?: return this
    memoryCacheKey(key)
    diskCacheKey(key)
    return this
}

class UrlStringKeyer : Keyer<String> {
    override fun key(data: String, options: Options): String? = ImageCacheKeys.of(data)
}

class UrlUriKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? = ImageCacheKeys.of(data.toString())
}
