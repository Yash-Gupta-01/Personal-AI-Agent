package com.aether.domain.action

import java.time.Instant
import java.util.UUID

/**
 * An explicit record of an action proposed and taken by Aether on the user's behalf.
 *
 * Enforces legal state transitions so that actions cannot jump states
 * (e.g. DISMISSED -> EXECUTED or EXPIRED -> EXECUTED).
 */
data class ActionRecord(
    val id: String,
    val threadId: String,
    val toolName: String,
    val toolParams: Map<String, String>,
    val status: ActionStatus,
    val proposedAt: Instant,
    val executedAt: Instant? = null,
    val observedAt: Instant? = null,
    val followUpDueAt: Instant? = null,
    val outcomeNote: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Action id must not be blank" }
        require(threadId.isNotBlank()) { "Thread id must not be blank" }
        require(toolName.isNotBlank()) { "Tool name must not be blank" }
    }

    /**
     * Checks if transitioning from the current [status] to [target] is permitted.
     */
    fun canTransitionTo(target: ActionStatus): Boolean = when (status) {
        ActionStatus.PROPOSED -> target in setOf(
            ActionStatus.POSTED,
            ActionStatus.APPROVED,
            ActionStatus.EXECUTED,
            ActionStatus.DISMISSED,
            ActionStatus.EXPIRED,
            ActionStatus.FAILED,
            ActionStatus.SUPPRESSED,
            ActionStatus.CANCELLED,
        )
        ActionStatus.POSTED -> target in setOf(
            ActionStatus.APPROVED,
            ActionStatus.EXECUTED,
            ActionStatus.DISMISSED,
            ActionStatus.EXPIRED,
            ActionStatus.FAILED,
            ActionStatus.CANCELLED,
        )
        ActionStatus.APPROVED -> target in setOf(
            ActionStatus.EXECUTED,
            ActionStatus.DISMISSED,
            ActionStatus.EXPIRED,
            ActionStatus.FAILED,
            ActionStatus.CANCELLED,
        )
        ActionStatus.EXECUTED -> target in setOf(
            ActionStatus.OBSERVED,
            ActionStatus.EVALUATED,
            ActionStatus.FAILED,
        )
        ActionStatus.OBSERVED -> target in setOf(
            ActionStatus.EVALUATED,
            ActionStatus.EXPIRED,
        )
        ActionStatus.EVALUATED,
        ActionStatus.DISMISSED,
        ActionStatus.EXPIRED,
        ActionStatus.FAILED,
        ActionStatus.SUPPRESSED,
        ActionStatus.CANCELLED -> false
    }

    /**
     * Transitions this action to [newStatus], validating state invariants.
     *
     * @throws IllegalStateException if the transition is illegal.
     */
    fun transitionTo(
        newStatus: ActionStatus,
        now: Instant = Instant.now(),
        followUpDueAt: Instant? = this.followUpDueAt,
        outcomeNote: String? = this.outcomeNote,
    ): ActionRecord {
        check(canTransitionTo(newStatus)) {
            "Illegal action state transition: cannot transition from $status to $newStatus (action id: $id)"
        }

        return copy(
            status = newStatus,
            executedAt = if (newStatus == ActionStatus.EXECUTED && executedAt == null) now else executedAt,
            observedAt = if (newStatus == ActionStatus.OBSERVED && observedAt == null) now else observedAt,
            followUpDueAt = followUpDueAt,
            outcomeNote = outcomeNote ?: this.outcomeNote,
            updatedAt = now,
        )
    }

    companion object {
        fun propose(
            threadId: String,
            toolName: String,
            toolParams: Map<String, String>,
            now: Instant = Instant.now(),
            id: String = UUID.randomUUID().toString(),
        ): ActionRecord = ActionRecord(
            id = id,
            threadId = threadId,
            toolName = toolName,
            toolParams = toolParams,
            status = ActionStatus.PROPOSED,
            proposedAt = now,
            executedAt = null,
            observedAt = null,
            followUpDueAt = null,
            outcomeNote = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
