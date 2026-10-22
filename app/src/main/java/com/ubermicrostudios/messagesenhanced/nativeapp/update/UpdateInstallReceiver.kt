package com.ubermicrostudios.messagesenhanced.nativeapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.ubermicrostudios.messagesenhanced.nativeapp.R

/**
 * PackageInstaller delivers the session result here. A pending user action
 * carries the system confirm dialog, which has to be started by the app.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
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
