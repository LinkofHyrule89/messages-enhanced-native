package com.ubermicrostudios.messagesenhanced.nativeapp.update

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Pure update decisions. Android framework calls stay in the installer and the
 * receivers; these functions are what the unit tests lock in.
 */
object UpdatePolicy {
    const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    const val PACKAGE_REPLACED_ACTION = "android.intent.action.MY_PACKAGE_REPLACED"

    fun autoCheckDue(lastCheckAt: Long, now: Long, intervalMs: Long = CHECK_INTERVAL_MS): Boolean {
        return now - lastCheckAt >= intervalMs
    }

    /**
     * Android 12+ can commit a self-update with USER_ACTION_NOT_REQUIRED.
     * Older releases always confirm.
     */
    @ChecksSdkIntAtLeast(parameter = 0, api = Build.VERSION_CODES.S)
    fun requestSilentInstall(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

    /**
     * Android 14+ should label the session as a file this app downloaded, so the
     * installer does not treat it as a local package the user opened by hand.
     */
    @ChecksSdkIntAtLeast(parameter = 0, api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun markDownloadedFileSource(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Null when the length is unknown. Clamped to 0..100. */
    fun downloadPercent(read: Long, total: Long): Int? {
        if (total <= 0L) return null
        val percent = (read.coerceAtLeast(0L) * 100L) / total
        return percent.coerceIn(0L, 100L).toInt()
    }

    /**
     * Relaunch only for a package replace that this app's own installer started.
     * Any other update (adb, another installer) leaves the user where they were.
     */
    fun shouldRelaunch(action: String?, triggeredByApp: Boolean): Boolean {
        return triggeredByApp && action == PACKAGE_REPLACED_ACTION
    }

    /** Launch checks skip the offer dialog when auto-update is on. */
    fun installsWithoutOffer(autoUpdate: Boolean): Boolean = autoUpdate
}

sealed class UpdateProgress {
    data class Downloading(val read: Long, val total: Long) : UpdateProgress() {
        val percent: Int? get() = UpdatePolicy.downloadPercent(read, total)
    }

    data object Installing : UpdateProgress()
}
