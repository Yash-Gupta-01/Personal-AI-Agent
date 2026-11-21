package com.aether.action

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import com.aether.di.ApplicationScope
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.tools.alarm.SetAlarmTool
import com.aether.tools.notification.NotificationPromptTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Translucent trampoline Activity to launch the OEM Clock app from a 1-tap notification button.
 *
 * Android 12+ bans starting activities from BroadcastReceivers triggered by notifications.
 * This Activity is launched directly via PendingIntent.getActivity(), executes the handoff,
 * updates Room via applicationScope, and finishes immediately without animation.
 */
@AndroidEntryPoint
class AlarmActionTrampolineActivity : ComponentActivity() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var actionRecordRepository: ActionRecordRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionId = intent.getStringExtra(NotificationPromptTool.EXTRA_ACTION_ID)
        val notificationId = intent.getIntExtra(NotificationPromptTool.EXTRA_NOTIFICATION_ID, -1)
        val hour = intent.getIntExtra(NotificationPromptTool.EXTRA_HOUR, 7)
        val minute = intent.getIntExtra(NotificationPromptTool.EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(NotificationPromptTool.EXTRA_LABEL) ?: "Aether Routine"

        if (notificationId != -1) {
            NotificationManagerCompat.from(this).cancel(notificationId)
        }

        if (actionId != null) {
            applicationScope.launch {
                val record = actionRecordRepository.getById(actionId)
                if (record != null && record.canTransitionTo(ActionStatus.EXECUTED)) {
                    val now = Instant.now()
                    val followUpDue = now.plusSeconds(5400) // 90 min after execution
                    actionRecordRepository.save(
                        record.transitionTo(
                            newStatus = ActionStatus.EXECUTED,
                            now = now,
                            followUpDueAt = followUpDue,
                        ),
                    )
                    FollowUpCheckInReceiver.schedule(this@AlarmActionTrampolineActivity, actionId, followUpDue)
                }
            }
        }

        val clockIntent = SetAlarmTool.buildIntent(hour, minute, label)
        try {
            if (clockIntent.resolveActivity(packageManager) != null) {
                startActivity(clockIntent)
            } else if (actionId != null) {
                markFailed(actionId)
            }
        } catch (_: ActivityNotFoundException) {
            if (actionId != null) {
                markFailed(actionId)
            }
        }

        finish()
    }

    private fun markFailed(actionId: String) {
        applicationScope.launch {
            val record = actionRecordRepository.getById(actionId)
            if (record != null && record.canTransitionTo(ActionStatus.FAILED)) {
                actionRecordRepository.save(record.transitionTo(ActionStatus.FAILED))
            }
        }
    }
}
