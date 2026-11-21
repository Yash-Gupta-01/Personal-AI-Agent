package com.aether.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.aether.di.ApplicationScope
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.tools.notification.NotificationPromptTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class NotificationDismissReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var actionRecordRepository: ActionRecordRepository

    override fun onReceive(context: Context, intent: Intent) {
        val actionId = intent.getStringExtra(NotificationPromptTool.EXTRA_ACTION_ID)
        val notificationId = intent.getIntExtra(NotificationPromptTool.EXTRA_NOTIFICATION_ID, -1)

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        if (actionId != null) {
            applicationScope.launch {
                val record = actionRecordRepository.getById(actionId)
                if (record != null && record.canTransitionTo(ActionStatus.DISMISSED)) {
                    actionRecordRepository.save(record.transitionTo(ActionStatus.DISMISSED, now = Instant.now()))
                }
            }
        }
    }
}
