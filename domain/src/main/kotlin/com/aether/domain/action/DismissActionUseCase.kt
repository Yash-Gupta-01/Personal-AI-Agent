package com.aether.domain.action

import com.aether.domain.thread.TrackedThreadRepository
import java.time.Duration
import java.time.Instant

/**
 * Dismisses a proposed action (e.g. user tapped 'Not now' or swiped away card),
 * advancing its lifecycle to DISMISSED and updating the parent thread's restraint backoff.
 */
class DismissActionUseCase(
    private val actionRepository: ActionRecordRepository,
    private val threadRepository: TrackedThreadRepository,
) {
    suspend fun invoke(
        actionId: String,
        now: Instant = Instant.now(),
    ): ActionRecord? {
        val record = actionRepository.getById(actionId) ?: return null
        if (!record.canTransitionTo(ActionStatus.DISMISSED)) return null

        val dismissed = record.transitionTo(
            newStatus = ActionStatus.DISMISSED,
            now = now,
        )
        actionRepository.save(dismissed)

        // Increment thread restraint cooldown backoff upon dismissal
        val thread = threadRepository.getById(record.threadId)
        if (thread != null) {
            val updatedThread = thread.recordIntervention(
                cooldownDuration = Duration.ofHours(4),
                now = now,
            )
            threadRepository.save(updatedThread)
        }

        return dismissed
    }
}
