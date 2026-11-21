package com.aether.domain.timeline

import com.aether.domain.action.ActionRecord
import com.aether.domain.memory.Memory
import java.time.Instant
import java.time.LocalDate

/**
 * A discrete entry in the assistant timeline stream.
 *
 * Combines user thoughts, assistant interventions, execution receipts,
 * follow-up check-ins, and causal reflections into a unified stream.
 */
sealed interface TimelineItem {
    val id: String
    val timestamp: Instant

    /** A thought captured by the user (via text or voice). */
    data class Thought(
        val memory: Memory,
    ) : TimelineItem {
        override val id: String get() = "thought-${memory.id}"
        override val timestamp: Instant get() = memory.createdAt
    }

    /** An interactive assistant intervention proposing an action (1-tap setup). */
    data class Intervention(
        val action: ActionRecord,
    ) : TimelineItem {
        override val id: String get() = "intervention-${action.id}"
        override val timestamp: Instant get() = action.proposedAt
    }

    /** A receipt showing that an action was successfully executed (e.g. alarm set in Clock). */
    data class ExecutedReceipt(
        val action: ActionRecord,
    ) : TimelineItem {
        override val id: String get() = "receipt-${action.id}"
        override val timestamp: Instant get() = action.executedAt ?: action.proposedAt
    }

    /** An active follow-up check-in card asking the user if the routine was completed. */
    data class FollowUpCheckIn(
        val action: ActionRecord,
    ) : TimelineItem {
        override val id: String get() = "followup-${action.id}"
        override val timestamp: Instant get() = action.followUpDueAt ?: action.executedAt ?: action.proposedAt
    }

    /** A causal reflection explaining root cause diagnosis and subsequent adaptation. */
    data class EvaluatedReflection(
        val action: ActionRecord,
        val userReply: String,
        val blocker: String?,
        val reflectionNote: String?,
    ) : TimelineItem {
        override val id: String get() = "reflection-${action.id}"
        override val timestamp: Instant get() = action.followUpDueAt ?: action.executedAt ?: action.proposedAt
    }

    /** A receipt indicating the action was dismissed. */
    data class DismissedReceipt(
        val action: ActionRecord,
    ) : TimelineItem {
        override val id: String get() = "dismissed-${action.id}"
        override val timestamp: Instant get() = action.proposedAt
    }
}

/**
 * A day's worth of timeline entries, newest first.
 */
data class TimelineDay(
    val date: LocalDate,
    val items: List<TimelineItem>,
)
