package com.aether.action

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aether.di.ApplicationScope
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.domain.thread.TrackedThreadRepository
import com.aether.tools.notification.NotificationPromptTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Delivers the next-day follow-up check-in notification with a RemoteInput inline reply.
 */
@AndroidEntryPoint
class FollowUpCheckInReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var actionRecordRepository: ActionRecordRepository

    @Inject
    lateinit var trackedThreadRepository: TrackedThreadRepository

    @Inject
    lateinit var notificationPromptTool: NotificationPromptTool

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("FollowUpCheckInReceiver", "onReceive triggered, action=${intent.action}, extras=${intent.extras}")
        val actionId = intent.getStringExtra(NotificationPromptTool.EXTRA_ACTION_ID)
        android.util.Log.d("FollowUpCheckInReceiver", "actionId=$actionId")
        if (actionId == null) return

        applicationScope.launch {
            val action = actionRecordRepository.getById(actionId)
            android.util.Log.d("FollowUpCheckInReceiver", "fetched action=$action")
            if (action == null || action.status != ActionStatus.EXECUTED) return@launch

            val thread = trackedThreadRepository.getById(action.threadId)
            val title = thread?.title ?: "Routine Check-In"

            notificationPromptTool.postFollowUp(
                notificationId = 1002,
                actionId = action.id,
                title = title,
                body = "Did you make it to $title? If not, what got in the way?",
            )
            android.util.Log.d("FollowUpCheckInReceiver", "postFollowUp called")
        }
    }

    companion object {
        fun schedule(context: Context, actionId: String, triggerAt: Instant) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FollowUpCheckInReceiver::class.java).apply {
                putExtra(NotificationPromptTool.EXTRA_ACTION_ID, actionId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                actionId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.toEpochMilli(),
                pendingIntent,
            )
        }
    }
}
