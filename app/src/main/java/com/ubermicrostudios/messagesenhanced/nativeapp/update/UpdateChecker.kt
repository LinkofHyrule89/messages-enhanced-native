package com.ubermicrostudios.messagesenhanced.nativeapp.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.ubermicrostudios.messagesenhanced.nativeapp.BuildConfig
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class UpdateResult {
    data class Available(val release: AppRelease) : UpdateResult()
    data object UpToDate : UpdateResult()
    data class Failed(val message: String) : UpdateResult()
}

/** Install that is waiting for the unknown-sources setting to be turned on. */
data class AwaitingInstall(
    val release: AppRelease,
    val fromUser: Boolean,
)

/**
 * GitHub Releases updater. Uses a plain OkHttp client with no server cookie jar,
 * so a session cookie is never sent to GitHub.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    @param:Named("updates") private val http: OkHttpClient,
) {
    private val claimLock = Any()
    private val network = Mutex()
    private val installScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val progressListeners = CopyOnWriteArrayList<(UpdateProgress) -> Unit>()
    private val sessionLock = Any()
    private var sessionListening = false
    private var sessionFailed = false

    /** Release the user still needs install permission for. Process-scoped. */
    @Volatile
    var awaitingPermission: AwaitingInstall? = null

    /** Newer release found this process, until the user updates or dismisses it. */
    @Volatile
    var pendingOffer: AppRelease? = null

    /** True while a download or a committed session should keep the progress dialog. */
    @Volatile
    var progressDialogRequested: Boolean = false

    @Volatile
    var latestProgress: UpdateProgress? = null

    /** Set once the session is committed, until it fails. Success kills the process. */
    @Volatile
    var sessionCommitted: Boolean = false

    private var installJob: Job? = null

    var autoUpdate: Boolean
        get() = prefs.autoUpdate
        set(value) { prefs.autoUpdate = value }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installInProgress(): Boolean = installJob?.isActive == true || sessionCommitted

    fun addProgressListener(listener: (UpdateProgress) -> Unit) {
        progressListeners.add(listener)
    }

    fun removeProgressListener(listener: (UpdateProgress) -> Unit) {
        progressListeners.remove(listener)
    }

    fun cancelInstall() {
        if (sessionCommitted) return
        installJob?.cancel()
    }

    fun manageUnknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    /**
     * @param force true for Settings. Automatic checks run at most every 6 hours.
     * Returns null when an automatic check is not due.
     */
    suspend fun check(force: Boolean): UpdateResult? {
        if (!force && !claimAutoCheck()) return null
        if (force) prefs.lastUpdateCheckAt = System.currentTimeMillis()
        return network.withLock { withContext(Dispatchers.IO) { fetch() } }
    }

    /**
     * Bumps the install token so [UpdateInstallReceiver] ignores a stale session.
     * The download worker calls this; the screen that asked for the update does not
     * stay alive for the download.
     */
    internal fun prepareInstallSession(): Int {
        ensureSessionListener()
        return synchronized(sessionLock) {
            sessionFailed = false
            sessionCommitted = false
            latestProgress = null
            UpdateEvents.currentToken += 1
            UpdateEvents.currentToken
        }
    }

    /**
     * Downloads [release] and commits a self-update. One install runs at a time.
     * The in-app Update button does not call this; it enqueues [UpdateDownloadInstallWorker],
     * which calls [download] and [install] so the user can leave.
     */
    fun startInstall(release: AppRelease, showDialog: Boolean) {
        if (installInProgress()) {
            if (showDialog) progressDialogRequested = true
            return
        }
        progressDialogRequested = showDialog
        val token = prepareInstallSession()
        installJob = installScope.launch {
            try {
                pendingOffer = null
                val file = download(release) { read, total ->
                    publish(UpdateProgress.Downloading(read, total))
                }
                publish(UpdateProgress.Installing)
                withContext(Dispatchers.IO) { install(file, token) }
            } catch (e: CancellationException) {
                finishProgress(token)
                throw e
            } catch (e: Exception) {
                finishProgress(token)
                val detail = e.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.update_failed_generic)
                Toast.makeText(
                    context,
                    context.getString(R.string.update_failed, detail),
                    Toast.LENGTH_LONG,
                ).show()
                UpdateEvents.downloadFailed()
            }
        }
    }

    suspend fun download(release: AppRelease, onProgress: (read: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dest = destination(release.apkName)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                val request = Request.Builder().url(release.downloadUrl).build()
                val response = try {
                    http.newCall(request).execute()
                } catch (e: IOException) {
                    throw IOException("Couldn't reach GitHub.", e)
                }
                response.use {
                    if (!response.isSuccessful) {
                        error("Download failed (HTTP ${response.code}).")
                    }
                    val body = response.body ?: error("Download was empty.")
                    val expected = when {
                        release.size > 0 -> release.size
                        body.contentLength() > 0 -> body.contentLength()
                        else -> -1L
                    }
                    body.byteStream().use { input ->
                        dest.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var written = 0L
                            var lastPercent = -1
                            var lastReport = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                written += n
                                val percent = if (expected > 0) ((written * 100) / expected).toInt() else -1
                                val now = System.currentTimeMillis()
                                val report = if (expected > 0) percent != lastPercent else now - lastReport >= 200
                                if (report) {
                                    lastPercent = percent
                                    lastReport = now
                                    withContext(Dispatchers.Main) { onProgress(written, expected) }
                                }
                            }
                            withContext(Dispatchers.Main) { onProgress(written, expected) }
                        }
                    }
                }
                if (release.size > 0 && dest.length() != release.size) {
                    error("Download size does not match the release.")
                }
                val expectedHash = release.sha256
                if (expectedHash != null) {
                    val actual = digest.digest().toHex()
                    if (!actual.equals(expectedHash, ignoreCase = true)) {
                        error("Download checksum does not match the release.")
                    }
                }
                dest
            } catch (e: Exception) {
                dest.delete()
                throw e
            }
        }

    fun install(apk: File) {
        install(apk, UpdateEvents.currentToken)
    }

    private fun install(apk: File, token: Int) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apk.length())
            // Installing through our own session makes this app the installer of
            // record. Later updates then qualify for USER_ACTION_NOT_REQUIRED.
            // PackageInstaller has no setInstallerPackageName a normal app can
            // use to name itself; the calling session is what records it.
            setAppPackageName(context.packageName)
            if (UpdatePolicy.requestSilentInstall(Build.VERSION.SDK_INT)) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            if (UpdatePolicy.markDownloadedFileSource(Build.VERSION.SDK_INT)) {
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            }
        }
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION
                    putExtra(UpdateInstallReceiver.EXTRA_TOKEN, token)
                    UpdateDownloadSpec.read(context)?.writeTo(this)
                }
                val flags = PendingIntentFlags.forInstall()
                val pending = android.app.PendingIntent.getBroadcast(context, sessionId, callback, flags)
                // Persist before commit. STATUS_SUCCESS usually never runs: the
                // process is killed as the package is replaced, and
                // UpdatePackageReplacedReceiver relaunches from this flag.
                prefs.relaunchAfterUpdate = true
                session.commit(pending.intentSender)
                committed = true
                // A failure callback can land on the main thread before commit returns.
                synchronized(sessionLock) {
                    if (token == UpdateEvents.currentToken && !sessionFailed) {
                        sessionCommitted = true
                    }
                }
            }
        } catch (e: Exception) {
            if (committed) return
            synchronized(sessionLock) {
                if (token == UpdateEvents.currentToken) sessionCommitted = false
            }
            prefs.relaunchAfterUpdate = false
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private fun fetch(): UpdateResult {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = try {
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> return UpdateResult.UpToDate
                    in 200..299 -> response.body?.string().orEmpty()
                    else -> return UpdateResult.Failed("Couldn't check for updates (HTTP ${response.code}).")
                }
            }
        } catch (_: IOException) {
            return UpdateResult.Failed("Couldn't reach GitHub.")
        }
        val parsed = ReleaseParser.parse(body)
            ?: return UpdateResult.Failed("Couldn't read the latest release.")
        val current = SemVer.parse(BuildConfig.VERSION_NAME)
            ?: return UpdateResult.Failed("Couldn't read this app version.")
        if (parsed.version <= current) {
            pendingOffer = null
            return UpdateResult.UpToDate
        }
        val apk = parsed.apk ?: return UpdateResult.Failed("The latest release has no APK.")
        if (apk.downloadUrl.isBlank()) return UpdateResult.Failed("The latest release has no APK.")
        if (apk.digestInvalid) return UpdateResult.Failed("The release digest is not a sha256 checksum.")
        val release = AppRelease(
            version = parsed.version,
            tag = parsed.tag,
            notes = parsed.notes,
            apkName = apk.name,
            downloadUrl = apk.downloadUrl,
            size = apk.size,
            sha256 = apk.sha256,
        )
        pendingOffer = release
        return UpdateResult.Available(release)
    }

    private fun claimAutoCheck(now: Long = System.currentTimeMillis()): Boolean = synchronized(claimLock) {
        val last = prefs.lastUpdateCheckAt
        if (!UpdatePolicy.autoCheckDue(last, now, CHECK_INTERVAL_MS)) return false
        prefs.lastUpdateCheckAt = now
        true
    }

    private fun ensureSessionListener() {
        if (sessionListening) return
        sessionListening = true
        UpdateEvents.addSessionFailure { failedToken ->
            val apply = synchronized(sessionLock) {
                if (failedToken != UpdateEvents.currentToken) {
                    false
                } else {
                    sessionFailed = true
                    sessionCommitted = false
                    true
                }
            }
            if (apply) finishProgress(failedToken)
        }
    }

    private fun publish(progress: UpdateProgress) {
        latestProgress = progress
        UpdateNotifier.show(context, progress)
        progressListeners.forEach { listener -> listener(progress) }
    }

    private fun finishProgress(token: Int) {
        val cancelNotification = synchronized(sessionLock) {
            if (token != UpdateEvents.currentToken) {
                false
            } else {
                latestProgress = null
                progressDialogRequested = false
                true
            }
        }
        if (cancelNotification) UpdateNotifier.cancel(context)
    }

    private fun destination(name: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        val safe = leaf.filter { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '-' || ch == '_' }
        val fileName = if (safe.endsWith(".apk", ignoreCase = true) && safe != ".apk") safe else "update.apk"
        return File(dir, fileName)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private object PendingIntentFlags {
        fun forInstall(): Int {
            val mutable = if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0
            return android.app.PendingIntent.FLAG_UPDATE_CURRENT or mutable
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = UpdatePolicy.CHECK_INTERVAL_MS
    }
}
