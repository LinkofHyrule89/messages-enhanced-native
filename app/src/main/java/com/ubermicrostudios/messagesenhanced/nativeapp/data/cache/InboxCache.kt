package com.ubermicrostudios.messagesenhanced.nativeapp.data.cache

import android.content.Context
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.ConversationDto
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.JsonConfig
import com.ubermicrostudios.messagesenhanced.nativeapp.data.model.MessageDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conversations and the latest messages of recently opened chats, as JSON under filesDir.
 * Reads and writes stay on [Dispatchers.IO]. Memory is filled by [warm] so the first frame
 * can paint without touching disk on the main thread.
 */
@Singleton
class InboxCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.applicationContext.filesDir, "inbox_cache")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonConfig.json
    private val fileLock = Any()
    private val jobLock = Any()

    @Volatile
    private var conversations: List<ConversationDto>? = null

    @Volatile
    private var warmDone = false

    private val messages = ConcurrentHashMap<String, List<MessageDto>>()
    private var warmJob: Job? = null
    private var conversationJob: Job? = null
    private val messageJobs = HashMap<String, Job>()

    /** Starts the disk read. Safe to call more than once. Does not block. */
    fun warm() {
        synchronized(jobLock) {
            if (warmJob != null) return
            warmJob = scope.launch { warmBody() }
        }
    }

    /** Null until the conversation file has been read or a save has landed in memory. */
    fun peekConversations(): List<ConversationDto>? = conversations

    /**
     * Null when this chat has not been read yet.
     * An empty list means the cache was checked and this chat has no saved messages.
     */
    fun peekMessages(conversationId: String): List<MessageDto>? {
        if (conversationId.isBlank()) return emptyList()
        messages[conversationId]?.let { return it }
        return if (warmDone) emptyList() else null
    }

    suspend fun readConversations(): List<ConversationDto> {
        warm()
        warmJob?.join()
        return conversations.orEmpty()
    }

    /**
     * Messages saved for [conversationId]. Waits for [warm], then reads this chat's
     * file if it was not in the recent preload. Empty when nothing is cached.
     */
    suspend fun readMessages(conversationId: String): List<MessageDto> {
        if (conversationId.isBlank()) return emptyList()
        warm()
        warmJob?.join()
        messages[conversationId]?.let { return it }
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                messages[conversationId] ?: run {
                    val loaded = readThreadFile(conversationId)
                    // A save can land in memory while the file is read. Keep that newer list.
                    messages.putIfAbsent(conversationId, loaded) ?: loaded
                }
            }
        }
    }

    fun saveConversations(list: List<ConversationDto>) {
        conversations = list
        synchronized(jobLock) {
            conversationJob?.cancel()
            conversationJob = scope.launch {
                delay(DEBOUNCE_MS)
                val latest = conversations ?: return@launch
                synchronized(fileLock) { writeAtomic(conversationFile(), json.encodeToString(ConversationCacheFile(conversations = latest))) }
            }
        }
    }

    /** Keeps the newest [MAX_MESSAGES] and remembers this chat as recently opened. */
    fun saveMessages(conversationId: String, list: List<MessageDto>) {
        if (conversationId.isBlank()) return
        val trimmed = list.filter { !it.messageId.startsWith("local-") }.takeLast(MAX_MESSAGES)
        messages[conversationId] = trimmed
        synchronized(jobLock) {
            messageJobs[conversationId]?.cancel()
            messageJobs[conversationId] = scope.launch {
                delay(DEBOUNCE_MS)
                val latest = messages[conversationId].orEmpty()
                synchronized(fileLock) {
                    writeThread(conversationId, latest)
                    updateRecentLocked(conversationId)
                }
            }
        }
    }

    fun touch(conversationId: String) {
        if (conversationId.isBlank()) return
        scope.launch {
            synchronized(fileLock) { updateRecentLocked(conversationId) }
        }
    }

    private fun warmBody() {
        synchronized(fileLock) {
            if (conversations == null) conversations = readConversationFile()
            val ids = readRecentFile()
            for (id in ids) {
                if (!messages.containsKey(id)) messages[id] = readThreadFile(id)
            }
            warmDone = true
        }
    }

    private fun conversationFile(): File = File(root, "conversations.json")

    private fun recentFile(): File = File(root, "recent.json")

    private fun threadDir(): File = File(root, "threads")

    private fun threadFile(id: String): File {
        val hash = id.hashCode().toUInt().toString(16)
        val safe = buildString {
            for (ch in id) {
                if (ch.isLetterOrDigit() || ch == '-' || ch == '_') append(ch)
                if (length >= 40) break
            }
        }.ifBlank { "chat" }
        return File(threadDir(), "$safe-$hash.json")
    }

    private fun readConversationFile(): List<ConversationDto> {
        val file = conversationFile()
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return emptyList()
        val wrapped = runCatching { json.decodeFromString<ConversationCacheFile>(text).conversations }.getOrNull()
        if (wrapped != null) return wrapped
        return runCatching { json.decodeFromString<List<ConversationDto>>(text) }.getOrDefault(emptyList())
    }

    private fun readRecentFile(): List<String> {
        val file = recentFile()
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<RecentCacheFile>(text).ids }.getOrDefault(emptyList())
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_THREADS)
    }

    private fun readThreadFile(id: String): List<MessageDto> {
        val file = threadFile(id)
        if (!file.isFile) return emptyList()
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        if (text.isBlank()) return emptyList()
        val wrapped = runCatching { json.decodeFromString<ThreadCacheFile>(text) }.getOrNull()
        if (wrapped != null && (wrapped.conversationId.isBlank() || wrapped.conversationId == id)) {
            return wrapped.messages.takeLast(MAX_MESSAGES)
        }
        return emptyList()
    }

    private fun writeThread(id: String, list: List<MessageDto>) {
        writeAtomic(
            threadFile(id),
            json.encodeToString(ThreadCacheFile(conversationId = id, messages = list.takeLast(MAX_MESSAGES))),
        )
    }

    private fun updateRecentLocked(id: String) {
        val next = (listOf(id) + readRecentFile().filter { it != id }).distinct().take(MAX_THREADS)
        writeAtomic(recentFile(), json.encodeToString(RecentCacheFile(ids = next)))
        val keep = next.map { threadFile(it).name }.toSet()
        threadDir().listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) file.delete()
        }
    }

    private fun writeAtomic(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    @Serializable
    private data class ConversationCacheFile(
        val version: Int = 1,
        val conversations: List<ConversationDto> = emptyList(),
    )

    @Serializable
    private data class RecentCacheFile(
        val version: Int = 1,
        val ids: List<String> = emptyList(),
    )

    @Serializable
    private data class ThreadCacheFile(
        val version: Int = 1,
        val conversationId: String = "",
        val messages: List<MessageDto> = emptyList(),
    )

    companion object {
        const val MAX_MESSAGES = 50
        const val MAX_THREADS = 10
        private const val DEBOUNCE_MS = 400L
    }
}
