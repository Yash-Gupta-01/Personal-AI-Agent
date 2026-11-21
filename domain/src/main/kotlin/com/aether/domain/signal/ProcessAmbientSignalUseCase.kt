package com.aether.domain.signal

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.domain.policy.RestraintConfigRepository
import com.aether.domain.policy.RestraintPolicy
import com.aether.domain.policy.RestraintVerdict
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import java.time.ZoneId

/**
 * Evaluates ambient OS signals (e.g. morning unlock, alarm dismissal, night unlock)
 * without requiring the user to open the app or type a note.
 */
class ProcessAmbientSignalUseCase(
    private val threadRepository: TrackedThreadRepository,
    private val actionRepository: ActionRecordRepository,
    private val restraintPolicy: RestraintPolicy,
    private val restraintConfigRepository: RestraintConfigRepository? = null,
) {

    sealed interface SignalOutcome {
        /** An executed routine action is ready for a follow-up check-in. */
        data class CheckInPrompt(val action: ActionRecord) : SignalOutcome

        /** User was detected unlocking the phone deep at night; records sleep friction. */
        data class NightWakeRecorded(val threadId: String) : SignalOutcome

        /** Signal did not meet criteria for proactive action or was restrained. */
        object NoAction : SignalOutcome
    }

    suspend fun invoke(
        signal: AmbientSignal,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SignalOutcome {
        val now = signal.timestamp
        val config = restraintConfigRepository?.getConfig() ?: com.aether.domain.policy.RestraintConfig()

        when (signal) {
            is AmbientSignal.AlarmDismissed -> {
                // An alarm was just dismissed on device Clock
                val pendingAction = actionRepository.getLatestPendingFollowUp()
                if (pendingAction != null && pendingAction.status == ActionStatus.EXECUTED) {
                    val observed = pendingAction.transitionTo(ActionStatus.OBSERVED, now = now)
                    actionRepository.save(observed)
                    return SignalOutcome.CheckInPrompt(observed)
                }
            }

            is AmbientSignal.UserPresent -> {
                val hour = signal.localTime.hour

                // 1. Morning Unlock (06:00 to 10:00)
                if (hour in 6..10) {
                    val verdict = restraintPolicy.evaluate(
                        now = now,
                        zone = zone,
                        todayInterventionCount = 0,
                        threadCooldownUntil = null,
                        consecutiveDismissals = 0,
                        hasConcreteAction = true,
                        config = config,
                    )

                    if (verdict == RestraintVerdict.PERMITTED) {
                        val pendingAction = actionRepository.getLatestPendingFollowUp()
                        if (pendingAction != null && pendingAction.status == ActionStatus.EXECUTED) {
                            val observed = pendingAction.transitionTo(ActionStatus.OBSERVED, now = now)
                            actionRepository.save(observed)
                            return SignalOutcome.CheckInPrompt(observed)
                        }
                    }
                }

                // 2. Late Night Wakefulness (01:00 to 04:30)
                if (hour in 1..4) {
                    val sleepThread = threadRepository.getByTopic("sleep") ?: TrackedThread.create(
                        topic = "sleep",
                        title = "Bedtime Wind-Down",
                        now = now,
                    )
                    val updated = sleepThread.withBlocker("late_night_wakefulness", now)
                    threadRepository.save(updated)
                    return SignalOutcome.NightWakeRecorded(updated.id)
                }
            }
        }

        return SignalOutcome.NoAction
    }
}
