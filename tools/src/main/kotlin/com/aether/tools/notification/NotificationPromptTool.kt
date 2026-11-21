package com.aether.tools.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

/**
 * Handles OS-native notification shade interactions:
 * - 1-tap action buttons (Set Alarm)
 * - RemoteInput inline replies for next-day follow-up
 */
class NotificationPromptTool(
    private val context: Context,
) {

    init {
        createNotificationChannels()
    }

    fun postNudge(
        notificationId: Int,
        actionId: String,
        title: String,
        body: String,
        confirmButtonText: String,
        hour: Int,
        minute: Int,
        label: String,
    ) {
        val trampolineIntent = Intent().apply {
            setClassName(context.packageName, "com.aether.action.AlarmActionTrampolineActivity")
            putExtra(EXTRA_ACTION_ID, actionId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
            putExtra(EXTRA_LABEL, label)
        }

        val confirmPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            trampolineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent().apply {
            setClassName(context.packageName, "com.aether.action.NotificationDismissReceiver")
            putExtra(EXTRA_ACTION_ID, actionId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_lock_idle_alarm, confirmButtonText, confirmPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Not now", dismissPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun postFollowUp(
        notificationId: Int,
        actionId: String,
        title: String,
        body: String,
    ) {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Why? (e.g. slept late, feeling sore)")
            .build()

        val replyIntent = Intent().apply {
            setClassName(context.packageName, "com.aether.action.NotificationReplyReceiver")
            action = "com.aether.action.NOTIFICATION_REPLY"
            putExtra(EXTRA_ACTION_ID, actionId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2000,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent,
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(replyAction)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aether Routine Nudges",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Proactive routine interventions and check-in follow-ups"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "aether_nudge_channel"
        const val KEY_TEXT_REPLY = "key_text_reply"

        const val EXTRA_ACTION_ID = "extra_action_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_LABEL = "extra_label"
    }
}
