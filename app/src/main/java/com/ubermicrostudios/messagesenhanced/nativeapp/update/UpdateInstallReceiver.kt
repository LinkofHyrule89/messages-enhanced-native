package com.ubermicrostudios.messagesenhanced.nativeapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.data.prefs.AppPreferences

/**
 * PackageInstaller delivers the session result here. A pending user action
 * (first install, older Android, or this app is not yet the installer of
 * record) carries the system confirm dialog, which the app has to start.
 * STATUS_SUCCESS usually never runs to completion: replacing the package
 * kills the process. Relaunch is [UpdatePackageReplacedReceiver].
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val token = intent.getIntExtra(EXTRA_TOKEN, 0)
        if (token != UpdateEvents.currentToken) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            // The new process relaunches from the pref flag. Do not clear it here.
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                AppPreferences.writeRelaunchAfterUpdate(context, false)
                UpdateNotifier.cancel(context)
                UpdateEvents.sessionStatus(status, token)
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.update_failed_generic)
                Toast.makeText(
                    context,
                    context.getString(R.string.update_failed, detail),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object {
        const val ACTION = "com.ubermicrostudios.messagesenhanced.nativeapp.UPDATE_INSTALL"
        const val EXTRA_TOKEN = "update_session_token"

        private fun confirmIntent(intent: Intent): Intent? {
            return if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
        }
    }
}
