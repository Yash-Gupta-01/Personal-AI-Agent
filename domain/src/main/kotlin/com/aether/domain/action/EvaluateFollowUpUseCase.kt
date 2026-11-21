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

        val lower = response.lowercase().trim()
        val isSuccess = lower.contains("yes") || lower.contains("did") ||
                lower.contains("went") || lower.contains("made it") ||
                lower.contains("crushed") || lower.contains("done")

        val blocker = if (isSuccess) null else when {
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

        val updatedParams = if (blocker != null) {
            record.toolParams + ("blocker" to blocker)
        } else {
            record.toolParams
        }

        val evaluated = record.copy(toolParams = updatedParams).transitionTo(
            newStatus = ActionStatus.EVALUATED,
            now = now,
            outcomeNote = response,
        )
        actionRepository.save(evaluated)

        val thread = threadRepository.getById(record.threadId)
        if (thread != null) {
            if (isSuccess) {
                threadRepository.save(thread.recordSuccess(now))
            } else if (blocker != null) {
                threadRepository.save(thread.withBlocker(blocker, now))
            }
        }

        return evaluated
    }
}
