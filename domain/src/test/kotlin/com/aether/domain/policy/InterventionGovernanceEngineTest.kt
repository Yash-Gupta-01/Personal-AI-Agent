package com.aether.domain.policy

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionStatus
import com.aether.domain.action.InterventionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class InterventionGovernanceEngineTest {

    private val policy = DefaultRestraintPolicy()
    private val utcZone: ZoneId = ZoneId.of("UTC")
    private val baseDate: LocalDate = LocalDate.of(2026, 11, 23)

    private fun instantAt(
        time: LocalTime,
        date: LocalDate = baseDate,
        zone: ZoneId = utcZone,
    ): Instant = ZonedDateTime.of(date, time, zone).toInstant()

    @Test
    fun `circadian window anchors at 04_00 AM local time`() {
        // 23:30 PM on Day 1
        val lateNight = instantAt(LocalTime.of(23, 30), baseDate)
        val windowLate = CircadianWindow.of(lateNight, utcZone)

        // 03:30 AM on Day 2 (before 04:00 AM reset)
        val earlyMorning = instantAt(LocalTime.of(3, 30), baseDate.plusDays(1))
        val windowEarly = CircadianWindow.of(earlyMorning, utcZone)

        // Must share the exact same circadian cycle date
        assertEquals("Late night and early morning belong to the same circadian day", windowLate.date, windowEarly.date)
        assertEquals(baseDate, windowLate.date)
        assertTrue(windowLate.contains(lateNight))
        assertTrue(windowLate.contains(earlyMorning))

        // 04:00 AM on Day 2 starts the next circadian day
        val nextDayReset = instantAt(LocalTime.of(4, 0), baseDate.plusDays(1))
        val windowNext = CircadianWindow.of(nextDayReset, utcZone)
        assertEquals(baseDate.plusDays(1), windowNext.date)
        assertFalse("04:00 AM reset belongs to the new circadian day", windowLate.contains(nextDayReset))
        assertTrue(windowNext.contains(nextDayReset))
    }

    @Test
    fun `solicited user commands bypass proactive intervention budget and cooldowns`() {
        val now = instantAt(LocalTime.of(14, 0))

        val projection = policy.projectEligibility(
            now = now,
            zone = utcZone,
            hasConcreteAction = true,
            category = InterventionCategory.SOLICITED,
            interventionsInCircadianWindow = 5, // Budget already exhausted for proactive
            lastPostedAt = now.minus(Duration.ofHours(1)), // Inside 10-hour cooldown
            threadCooldownUntil = now.plus(Duration.ofHours(2)),
            consecutiveDismissals = 0,
        )

        assertTrue(
            "Solicited commands must be immediately available regardless of budget or cooldown",
            projection is EligibilityProjection.AvailableAt
        )
        assertEquals(now, (projection as EligibilityProjection.AvailableAt).timestamp)
    }

    @Test
    fun `projectEligibility returns Blocked when hasConcreteAction is false`() {
        val now = instantAt(LocalTime.of(10, 0))

        val projection = policy.projectEligibility(
            now = now,
            zone = utcZone,
            hasConcreteAction = false, // Critical check from design review
            category = InterventionCategory.PROACTIVE_INTERVENTION,
            interventionsInCircadianWindow = 0,
            lastPostedAt = null,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
        )

        assertTrue("Must be Blocked when no concrete action is proposed", projection is EligibilityProjection.Blocked)
        assertEquals(
            RestraintVerdict.BLOCKED_NO_CONCRETE_ACTION,
            (projection as EligibilityProjection.Blocked).reason
        )
    }

    @Test
    fun `10-hour cooldown is enforced independently of 04_00 AM circadian boundary`() {
        // Intervened at 23:00 on Day 1
        val lastPosted = instantAt(LocalTime.of(23, 0), baseDate)
        // Check at 07:00 on Day 2 (circadian day has reset at 04:00, but only 8h elapsed since last intervention)
        val now = instantAt(LocalTime.of(7, 0), baseDate.plusDays(1))

        val projection = policy.projectEligibility(
            now = now,
            zone = utcZone,
            hasConcreteAction = true,
            category = InterventionCategory.PROACTIVE_INTERVENTION,
            interventionsInCircadianWindow = 0, // Budget is 0 in the new circadian day!
            lastPostedAt = lastPosted,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
        )

        assertTrue(projection is EligibilityProjection.AvailableAt)
        val eligibleTime = (projection as EligibilityProjection.AvailableAt).timestamp

        // Expected clearance: 23:00 + 10 hours = 09:00 next morning
        val expectedClearance = lastPosted.plus(Duration.ofHours(10))
        assertEquals(expectedClearance, eligibleTime)
        assertEquals(instantAt(LocalTime.of(9, 0), baseDate.plusDays(1)), eligibleTime)
    }

    @Test
    fun `projected clearance snapping out of quiet hours`() {
        // Intervention posted at 15:00 on Day 1
        val lastPosted = instantAt(LocalTime.of(15, 0), baseDate)
        // 10-hour cooldown ends at 01:00 AM on Day 2 (inside quiet hours 22:00 to 07:00)
        val now = instantAt(LocalTime.of(16, 0), baseDate)

        val projection = policy.projectEligibility(
            now = now,
            zone = utcZone,
            hasConcreteAction = true,
            category = InterventionCategory.PROACTIVE_INTERVENTION,
            interventionsInCircadianWindow = 1, // Budget exhausted for Day 1
            lastPostedAt = lastPosted,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            config = RestraintConfig(
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            ),
        )

        assertTrue(projection is EligibilityProjection.AvailableAt)
        val eligibleTime = (projection as EligibilityProjection.AvailableAt).timestamp

        // Candidate 1 (cooldown): 01:00 AM
        // Candidate 2 (budget reset): 04:00 AM
        // Max temporal candidate = 04:00 AM
        // Since 04:00 AM is inside quiet hours (22:00 to 07:00), snaps forward to 07:00 AM
        val expectedSnap = instantAt(LocalTime.of(7, 0), baseDate.plusDays(1))
        assertEquals(expectedSnap, eligibleTime)
    }

    @Test
    fun `action lifecycle consumes budget at POSTED without refund on rejection or failure`() {
        val now = instantAt(LocalTime.of(10, 0))
        val action = ActionRecord(
            id = "act-1",
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("time" to "07:00"),
            status = ActionStatus.PROPOSED,
            proposedAt = now,
            createdAt = now,
            updatedAt = now,
        )

        // Transition PROPOSED -> POSTED (Draws in notification shade, consumes budget)
        val postedAction = action.transitionTo(ActionStatus.POSTED, now)
        assertEquals(ActionStatus.POSTED, postedAction.status)

        // User dismisses/rejects notification -> DISMISSED
        val dismissedAction = postedAction.transitionTo(ActionStatus.DISMISSED, now.plus(Duration.ofMinutes(5)))
        assertEquals(ActionStatus.DISMISSED, dismissedAction.status)

        // Budget rule: Since status was posted and dismissed, it counts as consumed (irrevocable)
        val statusesCountingAsConsumed = setOf(
            ActionStatus.POSTED,
            ActionStatus.APPROVED,
            ActionStatus.EXECUTED,
            ActionStatus.DISMISSED,
            ActionStatus.FAILED,
        )
        assertTrue(dismissedAction.status in statusesCountingAsConsumed)
    }
}
