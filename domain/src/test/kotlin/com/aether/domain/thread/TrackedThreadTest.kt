package com.aether.domain.thread

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TrackedThreadTest {

    private val fixedNow = Instant.parse("2026-11-21T10:00:00Z")

    @Test
    fun `recordMiss increments misses and resets consecutive successes`() {
        val thread = TrackedThread.create(
            topic = "gym",
            title = "Morning Gym",
            now = fixedNow,
        ).copy(consecutiveSuccesses = 5, consecutiveMisses = 0)

        val nextTime = fixedNow.plusSeconds(86400)
        val updated = thread.recordMiss(nextTime)

        assertEquals(1, updated.consecutiveMisses)
        assertEquals(0, updated.consecutiveSuccesses)
        assertEquals(nextTime, updated.lastObservedAt)
        assertEquals(nextTime, updated.updatedAt)
    }

    @Test
    fun `recordSuccess increments successes and resets consecutive misses`() {
        val thread = TrackedThread.create(
            topic = "gym",
            title = "Morning Gym",
            now = fixedNow,
        ).copy(consecutiveSuccesses = 0, consecutiveMisses = 3)

        val nextTime = fixedNow.plusSeconds(86400)
        val updated = thread.recordSuccess(nextTime)

        assertEquals(1, updated.consecutiveSuccesses)
        assertEquals(0, updated.consecutiveMisses)
        assertEquals(nextTime, updated.lastObservedAt)
        assertEquals(nextTime, updated.updatedAt)
    }

    @Test
    fun `recordIntervention sets cooldown`() {
        val thread = TrackedThread.create(
            topic = "gym",
            title = "Morning Gym",
            now = fixedNow,
        )

        val updated = thread.recordIntervention(
            cooldownDuration = Duration.ofHours(24),
            now = fixedNow,
        )

        assertEquals(fixedNow, updated.lastIntervenedAt)
        assertEquals(fixedNow.plus(Duration.ofHours(24)), updated.cooldownUntil)
    }

    @Test
    fun `withBlocker deduplicates and trims blockers`() {
        val thread = TrackedThread.create(
            topic = "gym",
            title = "Morning Gym",
            now = fixedNow,
        ).withBlocker(" sleep_deprivation ")
            .withBlocker("sleep_deprivation")
            .withBlocker("late_meetings")

        assertEquals(listOf("sleep_deprivation", "late_meetings"), thread.knownBlockers)
    }
}
