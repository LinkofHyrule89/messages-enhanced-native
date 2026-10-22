package com.ubermicrostudios.messagesenhanced.nativeapp.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ubermicrostudios.messagesenhanced.nativeapp.R

/** Ongoing download / install progress. Failures cancel it; the receiver toasts. */
internal object UpdateNotifier {
    const val NOTIFICATION_ID = 7101
    private const val CHANNEL_ID = "app_updates"

    fun show(context: Context, progress: UpdateProgress) {
        val app = context.applicationContext
        if (!notificationsAllowed(app)) return
        ensureChannel(app)
        val open = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val content = open?.let { launch ->
            PendingIntent.getActivity(
                app,
                NOTIFICATION_ID,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(app, R.color.accent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (content != null) builder.setContentIntent(content)
        when (progress) {
            is UpdateProgress.Downloading -> {
                val percent = progress.percent
                builder.setContentTitle(app.getString(R.string.update_downloading))
                if (percent == null) {
                    builder.setContentText(app.getString(R.string.update_progress_unknown))
                    builder.setProgress(100, 0, true)
                } else {
                    builder.setContentText(app.getString(R.string.update_progress, percent))
                    builder.setProgress(100, percent, false)
                }
            }
            UpdateProgress.Installing -> {
                val title = app.getString(R.string.update_installing)
                builder.setContentTitle(title)
                    .setContentText(title)
                    .setProgress(100, 0, true)
            }
        }
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        try {
            NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_updates),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
