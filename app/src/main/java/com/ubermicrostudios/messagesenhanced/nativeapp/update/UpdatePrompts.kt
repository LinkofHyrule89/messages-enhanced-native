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
import kotlinx.coroutines.launch

/**
 * Update dialogs for the activity that is currently in front.
 * Launch checks are quiet except when a newer release exists.
 * Auto-update downloads and installs with no dialog; the notification carries progress.
 */
class UpdatePrompts(
    private val activity: AppCompatActivity,
    private val checker: UpdateChecker,
) {
    private var launchChecked = false
    private var manualBusy = false
    private var progressDialog: AlertDialog? = null
    private var progressBinding: DialogUpdateProgressBinding? = null
    private var offerDialog: AlertDialog? = null
    private var closingDialogs = false

    private val progressListener: (UpdateProgress) -> Unit = { progress ->
        if (!activity.isDestroyed) renderProgress(progress)
    }

    private val dismissProgress: () -> Unit = {
        if (!activity.isDestroyed) {
            progressDialog?.dismiss()
            progressDialog = null
            progressBinding = null
        }
    }

    init {
        checker.addProgressListener(progressListener)
        UpdateEvents.addDismisser(dismissProgress)
    }

    fun onHostResume() {
        if (activity.isFinishing || activity.isDestroyed) return
        val waiting = checker.awaitingPermission
        if (waiting != null) {
            if (!checker.canInstallPackages()) {
                checker.awaitingPermission = null
                toast(activity.getString(R.string.update_install_permission))
            } else {
                checker.awaitingPermission = null
                startDownload(waiting.release, waiting.showDialog)
            }
        }
        if (checker.installInProgress() && checker.progressDialogRequested) {
            ensureProgressDialog()
        }
    }

    fun checkOnLaunch() {
        if (activity.isFinishing) return
        if (checker.installInProgress()) {
            launchChecked = true
            if (checker.progressDialogRequested) ensureProgressDialog()
            return
        }
        val waiting = checker.pendingOffer
        if (waiting != null) {
            launchChecked = true
            presentFound(waiting)
            return
        }
        if (launchChecked) return
        launchChecked = true
        activity.lifecycleScope.launch {
            val result = checker.check(force = false)
            if (result is UpdateResult.Available) presentFound(result.release)
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
        checker.removeProgressListener(progressListener)
        UpdateEvents.removeDismisser(dismissProgress)
        progressDialog?.dismiss()
        progressDialog = null
        progressBinding = null
        offerDialog?.dismiss()
        offerDialog = null
    }

    private fun presentFound(release: AppRelease) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (UpdatePolicy.installsWithoutOffer(checker.autoUpdate)) {
            beginInstall(release, showDialog = false)
        } else {
            showOffer(release)
        }
    }

    private fun showOffer(release: AppRelease) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (offerDialog?.isShowing == true) return
        val notes = release.notes.ifBlank { activity.getString(R.string.update_no_notes) }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(release.tag)
            .setMessage(notes)
            .setPositiveButton(R.string.update_action) { _, _ -> beginInstall(release, showDialog = true) }
            .setNegativeButton(R.string.update_later) { _, _ -> checker.pendingOffer = null }
            .setOnCancelListener {
                if (!closingDialogs) checker.pendingOffer = null
            }
            .create()
        offerDialog = dialog
        dialog.show()
    }

    private fun beginInstall(release: AppRelease, showDialog: Boolean) {
        checker.pendingOffer = null
        if (checker.installInProgress()) {
            if (showDialog) {
                checker.progressDialogRequested = true
                ensureProgressDialog()
            }
            return
        }
        if (!checker.canInstallPackages()) {
            checker.awaitingPermission = AwaitingInstall(release, showDialog)
            try {
                activity.startActivity(checker.manageUnknownSourcesIntent())
            } catch (_: ActivityNotFoundException) {
                checker.awaitingPermission = null
                toast(activity.getString(R.string.update_install_permission))
            }
            return
        }
        startDownload(release, showDialog)
    }

    private fun startDownload(release: AppRelease, showDialog: Boolean) {
        if (showDialog) ensureProgressDialog()
        checker.startInstall(release, showDialog)
    }

    private fun ensureProgressDialog() {
        if (progressDialog?.isShowing == true) return
        if (activity.isFinishing || activity.isDestroyed) return
        val binding = DialogUpdateProgressBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_downloading)
            .setView(binding.root)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> checker.cancelInstall() }
            .create()
        progressBinding = binding
        progressDialog = dialog
        dialog.setOnDismissListener {
            if (progressDialog === dialog) {
                progressDialog = null
                progressBinding = null
            }
        }
        dialog.show()
        checker.latestProgress?.let { renderProgress(it) }
    }

    private fun renderProgress(progress: UpdateProgress) {
        val dialog = progressDialog ?: return
        val binding = progressBinding ?: return
        if (!dialog.isShowing || activity.isDestroyed) return
        when (progress) {
            is UpdateProgress.Downloading -> {
                dialog.setTitle(R.string.update_downloading)
                val percent = progress.percent
                if (percent != null) {
                    binding.progress.isIndeterminate = false
                    binding.progress.progress = percent
                    binding.progressText.text = activity.getString(R.string.update_progress, percent)
                } else {
                    binding.progress.isIndeterminate = true
                    binding.progressText.text = activity.getString(R.string.update_progress_unknown)
                }
            }
            UpdateProgress.Installing -> {
                dialog.setTitle(R.string.update_installing)
                binding.progress.isIndeterminate = true
                binding.progressText.text = activity.getString(R.string.update_installing)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
            }
        }
    }

    private fun toast(message: String) {
        if (activity.isDestroyed) return
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
