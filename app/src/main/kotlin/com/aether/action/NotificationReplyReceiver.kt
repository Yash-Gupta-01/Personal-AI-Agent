package com.aether.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
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
 * Handles RemoteInput inline replies from the notification shade
 * (e.g. "Closed it, didn't get enough sleep").
 */
@AndroidEntryPoint
class NotificationReplyReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var actionRecordRepository: ActionRecordRepository

    @Inject
    lateinit var trackedThreadRepository: TrackedThreadRepository

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("NotificationReplyReceiver", "onReceive triggered, extras=${intent.extras}")
        val actionId = intent.getStringExtra(NotificationPromptTool.EXTRA_ACTION_ID)
        val notificationId = intent.getIntExtra(NotificationPromptTool.EXTRA_NOTIFICATION_ID, -1)

        val bundle = RemoteInput.getResultsFromIntent(intent)
        val replyText = bundle?.getCharSequence(NotificationPromptTool.KEY_TEXT_REPLY)?.toString()
        android.util.Log.d("NotificationReplyReceiver", "actionId=$actionId, replyText=$replyText")

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (actionId != null && !replyText.isNullOrBlank()) {
            applicationScope.launch {
                val record = actionRecordRepository.getById(actionId)
                android.util.Log.d("NotificationReplyReceiver", "record=$record")
                if (record != null && record.canTransitionTo(ActionStatus.EVALUATED)) {
                    val now = Instant.now()
                    actionRecordRepository.save(
                        record.transitionTo(
                            newStatus = ActionStatus.EVALUATED,
                            now = now,
                            outcomeNote = replyText,
                        ),
                    )

                    // Learn the blocker on the tracked thread
                    val thread = trackedThreadRepository.getById(record.threadId)
                    if (thread != null) {
                        val blocker = extractSimpleBlocker(replyText)
                        trackedThreadRepository.save(thread.withBlocker(blocker, now))
                        android.util.Log.d("NotificationReplyReceiver", "attached blocker=$blocker to thread=${thread.id}")
                    }
                }
            }
        }
    }

    private fun extractSimpleBlocker(text: String): String = when {
        text.contains("sleep", ignoreCase = true) || text.contains("tired", ignoreCase = true) -> "sleep_debt"
        text.contains("work", ignoreCase = true) || text.contains("meeting", ignoreCase = true) -> "work_overrun"
        text.contains("sore", ignoreCase = true) || text.contains("sick", ignoreCase = true) || text.contains("ill", ignoreCase = true) -> "physical_limitation"
        else -> text.take(30).trim()
    }
}
