package com.ubermicrostudios.messagesenhanced.nativeapp.update

import android.content.ActivityNotFoundException
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.databinding.DialogUpdateProgressBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Update dialogs for the activity that is currently in front.
 * Launch checks are quiet except when a newer release exists.
 */
class UpdatePrompts(
    private val activity: AppCompatActivity,
    private val checker: UpdateChecker,
) {
    private var launchChecked = false
    private var manualBusy = false
    private var downloadJob: Job? = null
    private var progressDialog: AlertDialog? = null
    private var offerDialog: AlertDialog? = null
    private var closingDialogs = false

    fun onHostResume() {
        val release = checker.awaitingPermission ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (!checker.canInstallPackages()) {
            checker.awaitingPermission = null
            toast(activity.getString(R.string.update_install_permission))
            return
        }
        checker.awaitingPermission = null
        startDownload(release)
    }

    fun checkOnLaunch() {
        if (activity.isFinishing) return
        val waiting = checker.pendingOffer
        if (waiting != null) {
            launchChecked = true
            showOffer(waiting)
            return
        }
        if (launchChecked) return
        launchChecked = true
        activity.lifecycleScope.launch {
            val result = checker.check(force = false)
            if (result is UpdateResult.Available) showOffer(result.release)
        }
    }

    fun checkFromSettings(button: View) {
        if (manualBusy || activity.isFinishing) return
        manualBusy = true
        button.isEnabled = false
        activity.lifecycleScope.launch {
            try {
                when (val result = checker.check(force = true)) {
                    is UpdateResult.Available -> showOffer(result.release)
                    is UpdateResult.Failed -> toast(activity.getString(R.string.update_failed, result.message))
                    UpdateResult.UpToDate, null -> toast(activity.getString(R.string.up_to_date))
                }
            } finally {
                manualBusy = false
                if (!activity.isDestroyed) button.isEnabled = true
            }
        }
    }

    fun dismiss() {
        closingDialogs = true
        progressDialog?.dismiss()
        progressDialog = null
        offerDialog?.dismiss()
        offerDialog = null
    }

    private fun showOffer(release: AppRelease) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (offerDialog?.isShowing == true) return
        val notes = release.notes.ifBlank { activity.getString(R.string.update_no_notes) }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(release.tag)
            .setMessage(notes)
            .setPositiveButton(R.string.update_action) { _, _ -> beginInstall(release) }
            .setNegativeButton(R.string.update_later) { _, _ -> checker.pendingOffer = null }
            .setOnCancelListener {
                if (!closingDialogs) checker.pendingOffer = null
            }
            .create()
        offerDialog = dialog
        dialog.show()
    }

    private fun beginInstall(release: AppRelease) {
        checker.pendingOffer = null
        if (!checker.canInstallPackages()) {
            checker.awaitingPermission = release
            try {
                activity.startActivity(checker.manageUnknownSourcesIntent())
            } catch (_: ActivityNotFoundException) {
                checker.awaitingPermission = null
                toast(activity.getString(R.string.update_install_permission))
            }
            return
        }
        startDownload(release)
    }

    private fun startDownload(release: AppRelease) {
        if (downloadJob?.isActive == true) return
        val binding = DialogUpdateProgressBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_downloading)
            .setView(binding.root)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> downloadJob?.cancel() }
            .create()
        progressDialog = dialog
        dialog.show()
        downloadJob = activity.lifecycleScope.launch {
            try {
                val file = checker.download(release) { read, total ->
                    if (activity.isDestroyed) return@download
                    if (total > 0) {
                        val percent = ((read * 100) / total).toInt().coerceIn(0, 100)
                        binding.progress.isIndeterminate = false
                        binding.progress.progress = percent
                        binding.progressText.text = activity.getString(R.string.update_progress, percent)
                    } else {
                        binding.progress.isIndeterminate = true
                        binding.progressText.text = activity.getString(R.string.update_progress_unknown)
                    }
                }
                if (!activity.isDestroyed) dialog.dismiss()
                withContext(Dispatchers.IO) { checker.install(file) }
            } catch (e: CancellationException) {
                if (!activity.isDestroyed) dialog.dismiss()
                throw e
            } catch (e: Exception) {
                if (!activity.isDestroyed) dialog.dismiss()
                val detail = e.message?.takeIf { it.isNotBlank() }
                    ?: activity.getString(R.string.update_failed_generic)
                toast(activity.getString(R.string.update_failed, detail))
            } finally {
                if (progressDialog === dialog) progressDialog = null
            }
        }
    }

    private fun toast(message: String) {
        if (activity.isDestroyed) return
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
