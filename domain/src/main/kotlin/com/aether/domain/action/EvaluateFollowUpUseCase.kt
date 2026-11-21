package com.aether.domain.action

import com.aether.domain.thread.TrackedThreadRepository
import java.time.Instant

/**
 * Evaluates user response to a follow-up check-in.
 *
 * Marks the action as EVALUATED, diagnoses root blockers from the response,
 * or records routine success.
 */
class EvaluateFollowUpUseCase(
    private val actionRepository: ActionRecordRepository,
    private val threadRepository: TrackedThreadRepository,
) {
    suspend fun invoke(
        actionId: String,
        response: String,
        now: Instant = Instant.now(),
    ): ActionRecord? {
        val record = actionRepository.getById(actionId) ?: return null
        if (!record.canTransitionTo(ActionStatus.EVALUATED)) return null

        val evaluated = record.transitionTo(
            newStatus = ActionStatus.EVALUATED,
            now = now,
        )
        actionRepository.save(evaluated)

        val thread = threadRepository.getById(record.threadId)
        if (thread != null) {
            val lower = response.lowercase().trim()
            val isSuccess = lower.contains("yes") || lower.contains("did") ||
                    lower.contains("went") || lower.contains("made it") ||
                    lower.contains("crushed") || lower.contains("done")

            if (isSuccess) {
                threadRepository.save(thread.recordSuccess(now))
            } else {
                // Diagnose root blockers
                val blocker = when {
                    lower.contains("sleep") || lower.contains("late") ||
                            lower.contains("tired") || lower.contains("groggy") ||
                            lower.contains("alarm") || lower.contains("bed") -> "sleep_debt"
                    lower.contains("sore") || lower.contains("hurt") ||
                            lower.contains("pain") || lower.contains("injury") ||
                            lower.contains("injured") -> "physical_soreness"
                    lower.contains("work") || lower.contains("meeting") ||
                            lower.contains("deadline") || lower.contains("busy") ||
                            lower.contains("overtime") -> "work_overtime"
                    else -> "unknown_friction"
                }
                threadRepository.save(thread.withBlocker(blocker, now))
            }
        }

        return evaluated
    }
}
