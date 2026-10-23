package com.ubermicrostudios.messagesenhanced.nativeapp.push

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ubermicrostudios.messagesenhanced.nativeapp.R
import com.ubermicrostudios.messagesenhanced.nativeapp.util.IdempotencyKeys
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * Sends a notification reply, or marks a conversation read, without opening the app.
 * The idempotency key is fixed when the work is queued so a retry does not send twice.
 */
class NotificationActionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, MessagingService.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.notif_sending_reply))
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        return foregroundInfo(notification)
    }

    override suspend fun doWork(): Result {
        val conv = inputData.getString(KEY_CONV).orEmpty()
        if (conv.isBlank()) return Result.failure()
        val entry = EntryPointAccessors.fromApplication(applicationContext, NotificationWorkerEntryPoint::class.java)
        return when (inputData.getString(KEY_ACTION)) {
            ACTION_REPLY -> reply(entry, conv)
            ACTION_READ -> {
                entry.messagesRepository().markRead(conv)
                Result.success()
            }
            else -> Result.failure()
        }
    }

    private suspend fun reply(entry: NotificationWorkerEntryPoint, conv: String): Result {
        val text = inputData.getString(KEY_TEXT).orEmpty()
        if (text.isBlank()) return Result.failure()
        val key = inputData.getString(KEY_IDEMPOTENCY).orEmpty().ifBlank { IdempotencyKeys.newKey() }
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val group = inputData.getBoolean(KEY_GROUP, false)
        val timestamp = inputData.getLong(KEY_TS, System.currentTimeMillis())
        val notifications = entry.conversationNotifications()
        return try {
            entry.messagesRepository().send(conv, text, idempotencyKey = key)
            notifications.appendSelfAndPost(conv, text, timestamp, title, group)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            notifications.showReplyFailed(conv, title)
            Result.failure()
        }
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                MessageNotificationIds.FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(MessageNotificationIds.FOREGROUND_ID, notification)
        }
    }

    companion object {
        const val ACTION_REPLY = "reply"
        const val ACTION_READ = "read"

        private const val KEY_ACTION = "action"
        private const val KEY_CONV = "conv"
        private const val KEY_TEXT = "text"
        private const val KEY_TITLE = "title"
        private const val KEY_GROUP = "group"
        private const val KEY_TS = "ts"
        private const val KEY_IDEMPOTENCY = "idempotency"

        fun enqueueReply(
            context: Context,
            conversationId: String,
            text: String,
            title: String,
            isGroup: Boolean,
        ): UUID {
            val request = OneTimeWorkRequestBuilder<NotificationActionWorker>()
                .setInputData(replyData(conversationId, text, title, isGroup))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
            return request.id
        }

        fun enqueueRead(context: Context, conversationId: String): UUID {
            val data = Data.Builder()
                .putString(KEY_ACTION, ACTION_READ)
                .putString(KEY_CONV, conversationId)
                .build()
            val request = OneTimeWorkRequestBuilder<NotificationActionWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueue(request)
            return request.id
        }

        private fun replyData(conversationId: String, text: String, title: String, isGroup: Boolean): Data {
            val key = IdempotencyKeys.newKey()
            val timestamp = System.currentTimeMillis()
            var body = text
            var data = buildReply(conversationId, body, title, isGroup, timestamp, key)
            while (data.toByteArray().size > Data.MAX_DATA_BYTES - 256 && body.length > 20) {
                body = body.take(body.length * 3 / 4)
                data = buildReply(conversationId, body, title, isGroup, timestamp, key)
            }
            return data
        }

        private fun buildReply(
            conversationId: String,
            text: String,
            title: String,
            isGroup: Boolean,
            timestamp: Long,
            key: String,
        ): Data = Data.Builder()
            .putString(KEY_ACTION, ACTION_REPLY)
            .putString(KEY_CONV, conversationId)
            .putString(KEY_TEXT, text)
            .putString(KEY_TITLE, title.take(300))
            .putBoolean(KEY_GROUP, isGroup)
            .putLong(KEY_TS, timestamp)
            .putString(KEY_IDEMPOTENCY, key)
            .build()
    }
}
