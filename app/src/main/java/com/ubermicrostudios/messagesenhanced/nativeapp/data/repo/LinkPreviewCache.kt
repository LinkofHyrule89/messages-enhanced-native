package com.ubermicrostudios.messagesenhanced.nativeapp.data.repo

import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.LinkPreviewDto
import com.ubermicrostudios.messagesenhanced.nativeapp.data.net.BaseUrlProvider
import com.ubermicrostudios.messagesenhanced.nativeapp.data.net.MessagesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory link preview cache. The card is fetched from `GET /api/link-preview?url=`
 * and its image (already rewritten to `/api/link-preview-image`) loads through Coil.
 */
@Singleton
class LinkPreviewCache @Inject constructor(
    private val api: MessagesApi,
    private val baseUrl: BaseUrlProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val memory = object : LinkedHashMap<String, LinkPreviewDto?>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkPreviewDto?>?): Boolean =
            size > 100
    }
    private val inflight = HashSet<String>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun peek(url: String): LinkPreviewDto? = synchronized(memory) { memory[url] }

    /** True once a request has finished, including a preview the server could not build. */
    fun known(url: String): Boolean = synchronized(memory) { memory.containsKey(url) }

    fun imageUrl(preview: LinkPreviewDto): String? = preview.absoluteImage(baseUrl.origin())

    fun request(url: String) {
        if (url.isBlank()) return
        synchronized(memory) {
            if (memory.containsKey(url) || !inflight.add(url)) return
        }
        scope.launch {
            val preview = runCatching { api.linkPreview(url) }.getOrNull()?.takeIf { it.usable() }
            synchronized(memory) {
                memory[url] = preview
                inflight.remove(url)
            }
            _revision.value = _revision.value + 1
        }
    }
}
