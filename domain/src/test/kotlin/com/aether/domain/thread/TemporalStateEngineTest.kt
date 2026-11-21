package com.aether.domain.thread

import com.aether.domain.action.BehavioralOutcome
import com.aether.domain.action.SituationalOutcome
import com.aether.domain.action.ToolExecutionOutcome
import com.aether.domain.action.TripartiteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TemporalStateEngineTest {

    private val t0: Instant = Instant.parse("2026-11-23T07:30:00.000Z")
    private val t0Plus48h: Instant = t0.plus(Duration.ofHours(48)) // 2026-11-25T07:30:00.000Z

    private fun createThread(topic: String = "gym", misses: Int = 2): TrackedThread = TrackedThread.create(
        topic = topic,
        title = "Morning Workout",
        now = t0,
    ).copy(consecutiveMisses = misses)

    @Test
    fun `sub-minute 48-hour boundary test enforces exact inclusive expiration`() {
        val thread = createThread(misses = 2)
            .recordObstacle("FEVER", ObstacleRelationship.NEW_EPISODE, t0)

        assertNotNull("Active obstacle must exist", thread.activeObstacle)
        assertEquals("Anchor T0 must match startedAt", t0, thread.activeObstacle?.startedAt)
        assertEquals("expiresAt must be T0 + 48h", t0Plus48h, thread.activeObstacle?.expiresAt)
        assertEquals("Observation count starts at 1", 1, thread.activeObstacle?.observationCount)

        // Boundary A: 1 millisecond before expiration -> PAUSED
        val tMinus1ms = t0Plus48h.minusMillis(1)
        val pausedCheck = thread.checkExpiration(tMinus1ms)
        assertNotNull("Obstacle must still be active before expiry", pausedCheck.activeObstacle)
        assertEquals("Streak remains paused with 2 misses", 2, pausedCheck.consecutiveMisses)
        assertTrue("Thread reports paused state", pausedCheck.isObstaclePaused)

        // Boundary B: Exactly at expiration (inclusive boundary: now >= expiresAt) -> EXPIRED
        val expiredCheck = thread.checkExpiration(t0Plus48h)
        assertNull("Obstacle must be cleared upon expiration", expiredCheck.activeObstacle)
        assertEquals("consecutiveMisses must reset to 0 for intervention triggering", 0, expiredCheck.consecutiveMisses)
        assertFalse("Thread is no longer paused", expiredCheck.isObstaclePaused)

        // Boundary C: After expiration, a new miss establishes consecutiveMisses = 1
        val newMissTime = t0Plus48h.plus(Duration.ofHours(2))
        val postExpiryMiss = thread.recordMiss(newMissTime)
        assertNull("Active obstacle remains cleared", postExpiryMiss.activeObstacle)
        assertEquals("New miss starts fresh streak", 1, postExpiryMiss.consecutiveMisses)
    }

    @Test
    fun `repeated reports of the same active obstacle increment count without extending expiration`() {
        val thread = createThread(misses = 1)
            .recordObstacle("FEVER", ObstacleRelationship.NEW_EPISODE, t0)

        // Day 2 (T0 + 24h): User notes "still sick with fever"
        val t24h = t0.plus(Duration.ofHours(24))
        val secondReport = thread.recordObstacle("FEVER", ObstacleRelationship.SAME_EPISODE, t24h)

        assertNotNull(secondReport.activeObstacle)
        assertEquals("Observation count must increment to 2", 2, secondReport.activeObstacle?.observationCount)
        assertEquals("startedAt must remain immutable T0", t0, secondReport.activeObstacle?.startedAt)
        assertEquals("expiresAt must strictly remain T0 + 48h", t0Plus48h, secondReport.activeObstacle?.expiresAt)
        assertEquals("lastObservedAt updates to check-in time", t24h, secondReport.lastObservedAt)

        // Day 3 (T0 + 40h): Third report
        val t40h = t0.plus(Duration.ofHours(40))
        val thirdReport = secondReport.recordObstacle("FEVER", ObstacleRelationship.SAME_EPISODE, t40h)
        assertEquals("Observation count increments to 3", 3, thirdReport.activeObstacle?.observationCount)
        assertEquals("expiresAt remains strictly unextended", t0Plus48h, thirdReport.activeObstacle?.expiresAt)
    }

    @Test
    fun `continuation report after expiration cannot reopen window`() {
        val thread = createThread(misses = 2)
            .recordObstacle("FEVER", ObstacleRelationship.NEW_EPISODE, t0)

        // Advance to T0 + 50h (expired)
        val t50h = t0.plus(Duration.ofHours(50))
        val expiredThread = thread.checkExpiration(t50h)
        assertNull("Obstacle must be expired", expiredThread.activeObstacle)

        // User reports "still recovering from that fever"
        val continuation = expiredThread.recordObstacle(
            obstacleType = "FEVER",
            relationship = ObstacleRelationship.CONTINUATION_OF_EXPIRED_EPISODE,
            now = t50h,
        )

        assertNull("Continuation of expired episode must NOT reopen obstacle window", continuation.activeObstacle)
        assertEquals("consecutiveMisses must remain 0", 0, continuation.consecutiveMisses)
        assertEquals("lastObservedAt is recorded for biographical fact", t50h, continuation.lastObservedAt)
    }

    @Test
    fun `unknown relationship fails safe without pausing streak`() {
        val thread = createThread(misses = 2)

        val reported = thread.recordObstacle(
            obstacleType = "GYM",
            relationship = ObstacleRelationship.UNKNOWN,
            now = t0,
        )

        assertNull("UNKNOWN relationship must NOT create an obstacle window", reported.activeObstacle)
        assertEquals("consecutiveMisses unchanged", 2, reported.consecutiveMisses)

        // Subsequent miss correctly increments
        val nextMiss = reported.recordMiss(t0.plus(Duration.ofHours(24)))
        assertEquals("Subsequent miss increments to 3", 3, nextMiss.consecutiveMisses)
    }

    @Test
    fun `genuinely new obstacle episode creates fresh discrete window`() {
        val thread = createThread(misses = 1)
            .recordObstacle("FEVER", ObstacleRelationship.NEW_EPISODE, t0)

        // Fast-forward past fever expiration
        val tExpired = t0.plus(Duration.ofHours(50))
        val expiredThread = thread.checkExpiration(tExpired)

        // User reports work travel
        val travelStart = t0.plus(Duration.ofHours(72))
        val newEpisode = expiredThread.recordObstacle(
            obstacleType = "TRAVEL",
            relationship = ObstacleRelationship.NEW_EPISODE,
            now = travelStart,
        )

        assertNotNull("New obstacle window must be created", newEpisode.activeObstacle)
        assertEquals("TRAVEL", newEpisode.activeObstacle?.obstacleType)
        assertEquals(travelStart, newEpisode.activeObstacle?.startedAt)
        assertEquals(travelStart.plus(Duration.ofHours(48)), newEpisode.activeObstacle?.expiresAt)
        assertEquals(1, newEpisode.activeObstacle?.observationCount)
    }

    @Test
    fun `explicit recovery clears active obstacle and resets miss streak`() {
        val thread = createThread(misses = 3)
            .recordObstacle("FEVER", ObstacleRelationship.NEW_EPISODE, t0)

        assertEquals(3, thread.consecutiveMisses)
        assertNotNull(thread.activeObstacle)

        // Day 4: Explicit recovery reported
        val recoveryTime = t0.plus(Duration.ofHours(36))
        val recovered = thread.recordRecovery(recoveryTime)

        assertNull("Obstacle window must be cleared upon recovery", recovered.activeObstacle)
        assertEquals("consecutiveMisses must reset to 0", 0, recovered.consecutiveMisses)
        assertEquals("consecutiveSuccesses sets to 1", 1, recovered.consecutiveSuccesses)
        assertEquals(ThreadStatus.ACTIVE, recovered.status)
        assertEquals(recoveryTime, recovered.lastObservedAt)
    }

    @Test
    fun `tripartite state models independent execution, compliance, and situational reality`() {
        val state = TripartiteState(
            toolOutcome = ToolExecutionOutcome.EXECUTED,
            behavioralOutcome = BehavioralOutcome.COMPLIANT,
            situationalOutcome = SituationalOutcome.OBSTACLE_PRESENT,
        )

        // Proves non-conflation: User went to sleep early (COMPLIANT) and alarm rang (EXECUTED),
        // yet still couldn't attend gym due to waking with fever (OBSTACLE_PRESENT).
        assertEquals(ToolExecutionOutcome.EXECUTED, state.toolOutcome)
        assertEquals(BehavioralOutcome.COMPLIANT, state.behavioralOutcome)
        assertEquals(SituationalOutcome.OBSTACLE_PRESENT, state.situationalOutcome)
    }
}
