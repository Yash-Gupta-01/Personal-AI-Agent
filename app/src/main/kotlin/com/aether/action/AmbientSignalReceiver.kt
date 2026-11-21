package com.aether.action

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aether.di.ApplicationScope
import com.aether.domain.signal.AmbientSignal
import com.aether.domain.signal.ProcessAmbientSignalUseCase
import com.aether.domain.thread.TrackedThreadRepository
import com.aether.tools.notification.NotificationPromptTool
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/**
 * Listens for passive native OS background events (user unlocking phone in the morning,
 * dismissals of alarms from OEM Clock app, or late night wakefulness) and triggers
 * ambient routine evaluation without requiring user to open the app.
 */
@AndroidEntryPoint
class AmbientSignalReceiver : BroadcastReceiver() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var processAmbientSignalUseCase: ProcessAmbientSignalUseCase

    @Inject
    lateinit var trackedThreadRepository: TrackedThreadRepository

    @Inject
    lateinit var notificationPromptTool: NotificationPromptTool

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        android.util.Log.d(TAG, "AmbientSignalReceiver received action: $action")

        val signal: AmbientSignal = when (action) {
            Intent.ACTION_USER_PRESENT,
            "com.aether.action.TEST_USER_PRESENT" -> {
                val testHour = intent.getIntExtra("test_hour", -1)
                val testMinute = intent.getIntExtra("test_minute", 0)
                val localTime = if (testHour in 0..23) {
                    LocalTime.of(testHour, testMinute)
                } else {
                    LocalTime.now()
                }
                AmbientSignal.UserPresent(
                    timestamp = Instant.now(),
                    localTime = localTime,
                )
            }
            "android.intent.action.ALARM_DISMISSED",
            "com.coloros.alarmclock.ALARM_DISMISSED",
            "com.oplus.alarmclock.ALARM_DISMISS" -> {
                val label = intent.getStringExtra("android.intent.extra.alarm.MESSAGE")
                    ?: intent.getStringExtra("label")
                AmbientSignal.AlarmDismissed(
                    timestamp = Instant.now(),
                    label = label,
                )
            }
            else -> return
        }

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                android.util.Log.d(TAG, "Processing signal: $signal")
                val outcome = processAmbientSignalUseCase.invoke(signal)
                android.util.Log.d(TAG, "Signal outcome: $outcome")
                when (outcome) {
                    is ProcessAmbientSignalUseCase.SignalOutcome.CheckInPrompt -> {
                        val thread = trackedThreadRepository.getById(outcome.action.threadId)
                        val title = thread?.title ?: "Routine Check-In"
                        notificationPromptTool.postFollowUp(
                            notificationId = 1002,
                            actionId = outcome.action.id,
                            title = title,
                            body = "Did you make it to $title? If not, what got in the way?",
                        )
                        android.util.Log.d(TAG, "Posted follow-up check-in for action: ${outcome.action.id}")
                    }
                    is ProcessAmbientSignalUseCase.SignalOutcome.NightWakeRecorded -> {
                        android.util.Log.d(TAG, "Recorded night wakefulness for thread: ${outcome.threadId}")
                    }
                    ProcessAmbientSignalUseCase.SignalOutcome.NoAction -> {
                        android.util.Log.d(TAG, "No action required for signal")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing ambient signal", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AmbientSignalReceiver"
    }
}
