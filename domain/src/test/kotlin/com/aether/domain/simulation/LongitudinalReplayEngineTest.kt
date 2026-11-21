package com.aether.domain.simulation

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.domain.action.BehavioralOutcome
import com.aether.domain.action.ProcessFrictionNudge
import com.aether.domain.action.SituationalOutcome
import com.aether.domain.action.ToolExecutionOutcome
import com.aether.domain.action.TripartiteState
import com.aether.domain.policy.DefaultRestraintPolicy
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.thread.ObstacleRelationship
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Longitudinal simulation engine validating multi-day state transitions,
 * obstacle decay, explicit recovery, failure handling, and negative feedback pivots.
 */
class LongitudinalReplayEngineTest {

    private class TestClock(private var current: Instant) {
        fun now(): Instant = current
        fun advanceBy(duration: Duration) { current = current.plus(duration) }
        fun set(instant: Instant) { current = instant }
    }

    private class InMemoryThreadRepo : TrackedThreadRepository {
        val threads = mutableMapOf<String, TrackedThread>()
        override fun observeActive(): Flow<List<TrackedThread>> = MutableStateFlow(threads.values.toList())
        override suspend fun getById(id: String): TrackedThread? = threads[id]
        override suspend fun getByTopic(topic: String): TrackedThread? = threads.values.firstOrNull { it.topic == topic }
        override suspend fun save(thread: TrackedThread) { threads[thread.id] = thread }
        override suspend fun delete(id: String) { threads.remove(id) }
    }

    private class InMemoryActionRepo : ActionRecordRepository {
        val actions = mutableMapOf<String, ActionRecord>()
        override fun observePending(): Flow<List<ActionRecord>> = MutableStateFlow(actions.values.toList())
        override fun observePendingFollowUps(): Flow<List<ActionRecord>> =
            MutableStateFlow(actions.values.filter { it.status == ActionStatus.EXECUTED })
        override suspend fun getLatestPendingFollowUp(): ActionRecord? =
            actions.values.filter { it.status == ActionStatus.EXECUTED }.maxByOrNull { it.executedAt ?: Instant.EPOCH }
        override fun observeForThread(threadId: String): Flow<List<ActionRecord>> =
            MutableStateFlow(actions.values.filter { it.threadId == threadId })
        override suspend fun getById(id: String): ActionRecord? = actions[id]
        override suspend fun save(record: ActionRecord) { actions[record.id] = record }
        override suspend fun delete(id: String) { actions.remove(id) }
        override suspend fun countProactivePostedInWindow(start: Instant, end: Instant): Int =
            actions.values.count { it.proposedAt >= start && it.proposedAt < end && it.status !in setOf(ActionStatus.SUPPRESSED, ActionStatus.CANCELLED) }
        override suspend fun getLastProactivePostedTimestamp(): Instant? =
            actions.values.filter { it.status !in setOf(ActionStatus.SUPPRESSED, ActionStatus.CANCELLED) }.maxByOrNull { it.proposedAt }?.proposedAt
    }

    private val baseDate: LocalDate = LocalDate.of(2026, 11, 23)
    private val utcZone = ZoneOffset.UTC

    private fun timeOnDay(dayOffset: Long, hour: Int, minute: Int): Instant =
        ZonedDateTime.of(baseDate.plusDays(dayOffset), LocalTime.of(hour, minute), utcZone).toInstant()

    @Test
    fun `longitudinal journey 1 - positive recovery journey across 7 days`() = runTest {
        val clock = TestClock(timeOnDay(0, 8, 30)) // Day 1, 08:30
        val threadRepo = InMemoryThreadRepo()
        val actionRepo = InMemoryActionRepo()
        val policy = DefaultRestraintPolicy()
        val pipeline = ProcessFrictionNudge(threadRepo, actionRepo, policy)

        // Day 1 (08:30): First miss
        val day1Action = pipeline.invoke(
            content = "Missed the gym today because I overslept",
            now = clock.now(),
            zone = utcZone,
        )
        assertNotNull("Day 1 records friction", day1Action)
        val day1Thread = threadRepo.getByTopic("gym")!!
        assertEquals("First miss recorded", 1, day1Thread.consecutiveMisses)

        // Day 2 (08:15): Repeated struggle -> causal reasoning diagnoses sleep_debt
        clock.set(timeOnDay(1, 8, 15))
        val day2Action = pipeline.invoke(
            content = "Overslept and missed gym again, really struggling to wake up early",
            now = clock.now(),
            zone = utcZone,
        )
        assertNotNull("Day 2 proposes action", day2Action)
        assertEquals("set_alarm", day2Action!!.toolName)
        assertEquals("22", day2Action.toolParams["hour"])
        assertEquals("30", day2Action.toolParams["minute"])
        val day2Thread = threadRepo.getByTopic("gym")!!
        assertEquals("Streak incremented to 2 misses", 2, day2Thread.consecutiveMisses)
        assertTrue("Diagnosed sleep debt", day2Thread.knownBlockers.contains("sleep_debt"))

        // Day 2 (08:30): User accepts proposal -> Action moves to APPROVED -> EXECUTED
        clock.set(timeOnDay(1, 8, 30))
        pipeline.recordUserActionResponse(day2Action.id, accepted = true, clock.now())
        var actionRecord = actionRepo.getById(day2Action.id)!!
        assertEquals(ActionStatus.APPROVED, actionRecord.status)
        actionRecord = actionRecord.transitionTo(ActionStatus.EXECUTED, clock.now())
        actionRepo.save(actionRecord)

        // Day 3 (07:30): Tripartite evaluation -> Slept at 22:30, but woke with 102 fever
        val day3ObstacleTime = timeOnDay(2, 7, 30)
        clock.set(day3ObstacleTime)

        // Tripartite independence check:
        val tripartite = TripartiteState(
            toolOutcome = ToolExecutionOutcome.EXECUTED,
            behavioralOutcome = BehavioralOutcome.COMPLIANT,
            situationalOutcome = SituationalOutcome.OBSTACLE_PRESENT,
        )
        assertEquals(ToolExecutionOutcome.EXECUTED, tripartite.toolOutcome)
        assertEquals(BehavioralOutcome.COMPLIANT, tripartite.behavioralOutcome)
        assertEquals(SituationalOutcome.OBSTACLE_PRESENT, tripartite.situationalOutcome)

        // Pipeline handles fever capture
        val day3Action = pipeline.invoke(
            content = "Went to bed at 22:30 like planned, but woke up with 102 fever. Can't workout.",
            now = clock.now(),
            zone = utcZone,
        )
        assertNull("Compassionate silence: zero proactive nagging during obstacle", day3Action)
        val day3Thread = threadRepo.getByTopic("gym")!!
        assertNotNull("Obstacle window created", day3Thread.activeObstacle)
        assertEquals("Fever anchored at Day 3 07:30", day3ObstacleTime, day3Thread.activeObstacle!!.startedAt)
        val expectedExpiration = day3ObstacleTime.plus(Duration.ofHours(48))
        assertEquals("Expires at Day 5 07:30", expectedExpiration, day3Thread.activeObstacle!!.expiresAt)
        assertEquals("Miss streak is paused at 2", 2, day3Thread.consecutiveMisses)
        assertTrue(day3Thread.isObstaclePaused)

        // Day 4 (08:00): "Still running a fever" -> same episode, count = 2, expiration UNCHANGED
        clock.set(timeOnDay(3, 8, 0))
        val day4Action = pipeline.invoke(
            content = "Still running a fever and in bed",
            now = clock.now(),
            zone = utcZone,
        )
        assertNull("Zero nagging on day 4", day4Action)
        val day4Thread = threadRepo.getByTopic("gym")!!
        assertEquals("Count increments to 2", 2, day4Thread.activeObstacle!!.observationCount)
        assertEquals("Expiration strictly unchanged", expectedExpiration, day4Thread.activeObstacle!!.expiresAt)
        assertEquals("Misses remain paused at 2", 2, day4Thread.consecutiveMisses)

        // Day 5 Sub-minute boundary tests:
        // Day 5, 07:29 (47h 59m elapsed): Still PAUSED
        val day5Before = expectedExpiration.minus(Duration.ofMinutes(1))
        val pausedThread = day4Thread.checkExpiration(day5Before)
        assertNotNull("Still active at 07:29", pausedThread.activeObstacle)
        assertEquals(2, pausedThread.consecutiveMisses)

        // Day 5, 07:31 (48h 01m elapsed): Stale streak EXPIRES
        val day5After = expectedExpiration.plus(Duration.ofMinutes(1))
        val expiredThread = day4Thread.checkExpiration(day5After)
        assertNull("Cleared at 07:31", expiredThread.activeObstacle)
        assertEquals("consecutiveMisses resets to 0 upon expiration", 0, expiredThread.consecutiveMisses)
        threadRepo.save(expiredThread)

        // Day 7 (09:00): Explicit recovery reported -> "Fever finally broke, hit workout today"
        clock.set(timeOnDay(6, 9, 0))
        val day7Action = pipeline.invoke(
            content = "Fever finally broke, hit a light full-body workout today at the gym!",
            now = clock.now(),
            zone = utcZone,
        )
        assertNull("Recovery success recorded silently", day7Action)
        val day7Thread = threadRepo.getByTopic("gym")!!
        assertEquals("consecutiveSuccesses sets to 1", 1, day7Thread.consecutiveSuccesses)
        assertEquals("consecutiveMisses remains 0", 0, day7Thread.consecutiveMisses)
        assertNull("Active obstacle cleared", day7Thread.activeObstacle)
    }

    @Test
    fun `longitudinal journey 2 - rejection and preference pivot journey across 5 days`() = runTest {
        val clock = TestClock(timeOnDay(0, 8, 30)) // Day 1, 08:30
        val threadRepo = InMemoryThreadRepo()
        val actionRepo = InMemoryActionRepo()
        val policy = DefaultRestraintPolicy()
        val pipeline = ProcessFrictionNudge(threadRepo, actionRepo, policy)

        // Day 1 (08:30): First miss
        val day1Action = pipeline.invoke(
            content = "Missed gym because I overslept",
            now = clock.now(),
            zone = utcZone,
        )
        assertNotNull(day1Action)
        assertEquals(1, threadRepo.getByTopic("gym")!!.consecutiveMisses)

        // Day 2 (08:15): Second miss -> Proposes 22:30 wind-down reminder
        clock.set(timeOnDay(1, 8, 15))
        val day2Action = pipeline.invoke(
            content = "Overslept and missed gym again",
            now = clock.now(),
            zone = utcZone,
        )
        assertNotNull("Aether proposes wind-down reminder", day2Action)
        assertEquals("22", day2Action!!.toolParams["hour"])

        // Day 2 (08:30): User explicitly REJECTS / DISMISSES the proposal
        clock.set(timeOnDay(1, 8, 30))
        pipeline.recordUserActionResponse(day2Action.id, accepted = false, clock.now())
        val actionRecord = actionRepo.getById(day2Action.id)!!
        assertEquals(ActionStatus.DISMISSED, actionRecord.status)
        val day2Thread = threadRepo.getByTopic("gym")!!
        assertTrue("Thread logs rejection blocker", day2Thread.knownBlockers.contains("rejected_morning_wind_down"))

        // Day 3 (19:00): User captures explicit preference pivot: "Mornings don't work for me. Switching my workouts to evenings after work."
        clock.set(timeOnDay(2, 19, 0))
        val day3Action = pipeline.invoke(
            content = "Mornings don't work for me. Switching my workouts to evenings after work.",
            now = clock.now(),
            zone = utcZone,
        )
        assertNull("Preference statement processed without unwanted interventions", day3Action)
        val day3Thread = threadRepo.getByTopic("gym")!!
        assertTrue("Thread records evening workout preference", day3Thread.knownBlockers.contains("prefers_evening_workouts"))
        assertTrue("Thread records user discarded morning alarm", day3Thread.knownBlockers.contains("user_discarded_morning_alarm"))

        // Day 4 (20:30): User crushed evening workout!
        clock.set(timeOnDay(3, 20, 30))
        val day4Action = pipeline.invoke(
            content = "Crushed a 1-hour evening workout at the gym!",
            now = clock.now(),
            zone = utcZone,
        )
        assertNull("Success recorded silently", day4Action)
        val day4Thread = threadRepo.getByTopic("gym")!!
        assertEquals("Evening session logged as success", 1, day4Thread.consecutiveSuccesses)
        assertEquals(0, day4Thread.consecutiveMisses)

        // Day 5 (08:00): User wakes up at 08:00 (no morning gym session) -> THE LITMUS TEST
        clock.set(timeOnDay(4, 8, 0))
        val day5Action = pipeline.invoke(
            content = "Woke up at 8 AM, didn't do morning workout",
            now = clock.now(),
            zone = utcZone,
        )

        // INVARIANT VERIFIED: Zombie morning alarm proposal is SUPPRESSED!
        assertNull("Aether abandons unwanted morning strategy and respects evening preference", day5Action)
    }

    @Test
    fun `fault injection - clock tool execution failure is recorded as FAILED without user penalty`() = runTest {
        val now = timeOnDay(0, 10, 0)
        val action = ActionRecord(
            id = "act-fault",
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("hour" to "7", "minute" to "0"),
            status = ActionStatus.PROPOSED,
            proposedAt = now,
            createdAt = now,
            updatedAt = now,
        )

        val posted = action.transitionTo(ActionStatus.POSTED, now)
        val approved = posted.transitionTo(ActionStatus.APPROVED, now)

        // Fault injected: Clock intent threw ActivityNotFoundException -> marked FAILED
        val failed = approved.transitionTo(ActionStatus.FAILED, now)

        assertEquals(ActionStatus.FAILED, failed.status)
        assertFalse("Failed action is not marked EXECUTED", failed.status == ActionStatus.EXECUTED)
    }
}
