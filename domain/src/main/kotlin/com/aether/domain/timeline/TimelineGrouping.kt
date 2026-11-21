package com.aether.domain.timeline

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionStatus
import com.aether.domain.memory.Memory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds a chronological day-grouped timeline combining user memories and proactive assistant actions.
 *
 * Days are sorted newest first. Within each day, items are sorted newest first.
 * If an intervention is proposed at the same timestamp as a captured thought, the intervention
 * is ordered before (above) the thought so the assistant's response is directly visible.
 */
fun buildTimeline(
    memories: List<Memory>,
    actions: List<ActionRecord>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TimelineDay> {
    val items = mutableListOf<TimelineItem>()

    // 1. Map memories
    memories.forEach { memory ->
        items.add(TimelineItem.Thought(memory))
    }

    // 2. Map actions to their appropriate timeline representation
    actions.forEach { action ->
        when (action.status) {
            ActionStatus.PROPOSED, ActionStatus.POSTED -> {
                items.add(TimelineItem.Intervention(action))
            }
            ActionStatus.APPROVED -> {
                items.add(TimelineItem.ExecutedReceipt(action))
            }
            ActionStatus.EXECUTED -> {
                // If follow-up is due or pending, surface as check-in, otherwise receipt
                if (action.followUpDueAt != null) {
                    items.add(TimelineItem.FollowUpCheckIn(action))
                } else {
                    items.add(TimelineItem.ExecutedReceipt(action))
                }
            }
            ActionStatus.OBSERVED -> {
                items.add(TimelineItem.FollowUpCheckIn(action))
            }
            ActionStatus.EVALUATED -> {
                items.add(
                    TimelineItem.EvaluatedReflection(
                        action = action,
                        userReply = action.outcomeNote ?: "",
                        blocker = action.toolParams["blocker"],
                        reflectionNote = action.toolParams["reflection_note"],
                    )
                )
            }
            ActionStatus.DISMISSED -> {
                items.add(TimelineItem.DismissedReceipt(action))
            }
            ActionStatus.FAILED, ActionStatus.EXPIRED, ActionStatus.SUPPRESSED, ActionStatus.CANCELLED -> {
                // Suppressed, expired or cancelled actions remain invisible to maintain quiet restraint
            }
        }
    }

    return items
        .sortedWith(
            compareByDescending<TimelineItem> { it.timestamp }
                .thenBy { item ->
                    // Priority ranking when timestamps match: interventions on top of thoughts
                    when (item) {
                        is TimelineItem.Intervention -> 0
                        is TimelineItem.FollowUpCheckIn -> 1
                        is TimelineItem.EvaluatedReflection -> 2
                        is TimelineItem.ExecutedReceipt -> 3
                        is TimelineItem.DismissedReceipt -> 4
                        is TimelineItem.Thought -> 5
                    }
                }
        )
        .groupBy { it.timestamp.toLocalDate(zone) }
        .map { (date, dayItems) -> TimelineDay(date, dayItems) }
        .sortedByDescending { it.date }
}

private fun Instant.toLocalDate(zone: ZoneId): LocalDate =
    atZone(zone).toLocalDate()
