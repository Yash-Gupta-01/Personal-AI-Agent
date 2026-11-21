package com.aether.domain.action

import java.time.Instant

/**
 * Executes a proposed or approved action, advancing its lifecycle to EXECUTED
 * and scheduling the follow-up check-in window.
 */
class ExecuteActionUseCase(
    private val actionRepository: ActionRecordRepository,
) {
    suspend fun invoke(
        actionId: String,
        now: Instant = Instant.now(),
        followUpDelaySeconds: Long = 5400, // +90m default
    ): ActionRecord? {
        val record = actionRepository.getById(actionId) ?: return null
        if (!record.canTransitionTo(ActionStatus.EXECUTED)) return null

        val followUpDue = now.plusSeconds(followUpDelaySeconds)
        val executed = record.transitionTo(
            newStatus = ActionStatus.EXECUTED,
            now = now,
            followUpDueAt = followUpDue,
        )
        actionRepository.save(executed)
        return executed
    }
}
