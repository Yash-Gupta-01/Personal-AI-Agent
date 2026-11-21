package com.aether.domain.timeline

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionStatus
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemorySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TimelineGroupingTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `empty inputs produce empty timeline`() {
        val days = buildTimeline(emptyList(), emptyList(), zone)
        assertTrue(days.isEmpty())
    }

    @Test
    fun `groups memories and actions into correct local days`() {
        val tToday = Instant.parse("2026-11-22T10:00:00Z")
        val tYesterday = Instant.parse("2026-11-21T18:00:00Z")

        val memoryToday = Memory.capture("Need to sleep early", MemorySource.MANUAL_TEXT, tToday)
        val memoryYesterday = Memory.capture("Missed gym today", MemorySource.MANUAL_TEXT, tYesterday)

        val actionYesterday = ActionRecord.propose(
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("hour" to "7", "minute" to "0"),
            now = tYesterday.plusSeconds(2),
        )

        val days = buildTimeline(listOf(memoryToday, memoryYesterday), listOf(actionYesterday), zone)

        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 11, 22), days[0].date)
        assertEquals(1, days[0].items.size)
        assertTrue(days[0].items[0] is TimelineItem.Thought)

        assertEquals(LocalDate.of(2026, 11, 21), days[1].date)
        assertEquals(2, days[1].items.size)
        // Action proposed 2 seconds after thought is ordered first (newest first)
        assertTrue(days[1].items[0] is TimelineItem.Intervention)
        assertTrue(days[1].items[1] is TimelineItem.Thought)
    }

    @Test
    fun `prioritizes intervention card over thought when timestamps are identical`() {
        val t = Instant.parse("2026-11-22T08:00:00Z")
        val thought = Memory.capture("didn't get to go to gym", MemorySource.MANUAL_TEXT, t)
        val action = ActionRecord.propose(
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("nudge_title" to "Morning Gym Routine"),
            now = t,
        )

        val days = buildTimeline(listOf(thought), listOf(action), zone)
        val day = days.single()

        assertEquals(2, day.items.size)
        assertTrue("Intervention must appear before thought when timestamps match", day.items[0] is TimelineItem.Intervention)
        assertTrue(day.items[1] is TimelineItem.Thought)
    }

    @Test
    fun `maps executed follow up and evaluated reflection correctly`() {
        val tExecuted = Instant.parse("2026-11-21T21:00:00Z")
        val tEvaluated = Instant.parse("2026-11-22T08:00:00Z")

        val executedAction = ActionRecord(
            id = "act-1",
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("hour" to "7"),
            status = ActionStatus.OBSERVED,
            proposedAt = tExecuted.minusSeconds(60),
            executedAt = tExecuted,
            observedAt = tExecuted,
            followUpDueAt = tExecuted.plusSeconds(3600),
            outcomeNote = null,
            createdAt = tExecuted.minusSeconds(60),
            updatedAt = tExecuted,
        )

        val evaluatedAction = ActionRecord(
            id = "act-2",
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("blocker" to "sleep_debt"),
            status = ActionStatus.EVALUATED,
            proposedAt = tEvaluated.minusSeconds(3600),
            executedAt = tEvaluated.minusSeconds(1800),
            observedAt = tEvaluated.minusSeconds(600),
            followUpDueAt = tEvaluated,
            outcomeNote = "Closed it due to lack of sleep",
            createdAt = tEvaluated.minusSeconds(3600),
            updatedAt = tEvaluated,
        )

        val days = buildTimeline(emptyList(), listOf(executedAction, evaluatedAction), zone)

        assertEquals(2, days.size)
        val today = days[0]
        val yesterday = days[1]

        val evaluatedItem = today.items.single() as TimelineItem.EvaluatedReflection
        assertEquals("Closed it due to lack of sleep", evaluatedItem.userReply)
        assertEquals("sleep_debt", evaluatedItem.blocker)

        val followUpItem = yesterday.items.single() as TimelineItem.FollowUpCheckIn
        assertEquals("act-1", followUpItem.action.id)
    }
}
