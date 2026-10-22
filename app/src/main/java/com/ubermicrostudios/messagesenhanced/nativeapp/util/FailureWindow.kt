package com.ubermicrostudios.messagesenhanced.nativeapp.util

/**
 * Shows an error only after [minFailures] consecutive failures spanning more than [minSpanMs].
 * A success clears the streak. Callers keep the last good data either way.
 * After startup or returning to the foreground, nothing is shown for [quietMs].
 */
class FailureWindow(
    private val minFailures: Int = 5,
    private val minSpanMs: Long = 30_000,
    private val quietMs: Long = 20_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var failures = 0
    private var firstAt = 0L
    private var quietUntil = 0L

    init {
        onForeground()
    }

    fun success() {
        failures = 0
        firstAt = 0L
    }

    /** Suppress the banner for [quietMs] after launch or returning to the foreground. */
    fun onForeground() {
        quietUntil = clock() + quietMs
    }

    /** Message to surface, or null while the streak is still short or quiet. */
    fun failure(message: String): String? {
        val now = clock()
        if (failures == 0) firstAt = now
        failures++
        if (now < quietUntil) return null
        return if (failures >= minFailures && now - firstAt > minSpanMs) message else null
    }
}

object NetErrors {
    fun friendly(error: Throwable): String {
        val raw = error.message.orEmpty()
        if (raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("No address associated with hostname", ignoreCase = true)
        ) {
            return "Can’t reach the server. Will keep trying."
        }
        return raw.ifBlank { "Could not refresh." }
    }
}
