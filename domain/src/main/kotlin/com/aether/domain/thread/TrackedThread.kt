package com.aether.domain.thread

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * An evolving habit, routine, or active life thread monitored by Aether.
 *
 * Rather than treating memories as isolated rows, threads track continuity
 * over time: consecutive successes, consecutive friction/misses, identified
 * blockers, and cooldown timers so the assistant knows when to act and when
 * to stay silent.
 */
data class TrackedThread(
    val id: String,
    val topic: String,
    val title: String,
    val status: ThreadStatus,
    val consecutiveMisses: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val knownBlockers: List<String> = emptyList(),
    val lastObservedAt: Instant,
    val lastIntervenedAt: Instant? = null,
    val cooldownUntil: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(id.isNotBlank()) { "Thread id must not be blank" }
        require(topic.isNotBlank()) { "Thread topic must not be blank" }
        require(title.isNotBlank()) { "Thread title must not be blank" }
        require(consecutiveMisses >= 0) { "consecutiveMisses must be >= 0" }
        require(consecutiveSuccesses >= 0) { "consecutiveSuccesses must be >= 0" }
    }

    /** Records a missed routine or friction event, resetting success streak. */
    fun recordMiss(now: Instant = Instant.now()): TrackedThread = copy(
        consecutiveMisses = consecutiveMisses + 1,
        consecutiveSuccesses = 0,
        lastObservedAt = now,
        updatedAt = now,
    )

    /** Records a successful completion of the routine, resetting miss streak. */
    fun recordSuccess(now: Instant = Instant.now()): TrackedThread = copy(
        consecutiveSuccesses = consecutiveSuccesses + 1,
        consecutiveMisses = 0,
        lastObservedAt = now,
        updatedAt = now,
    )

    /** Records an intervention and optionally sets a backoff cooldown. */
    fun recordIntervention(cooldownDuration: Duration? = null, now: Instant = Instant.now()): TrackedThread = copy(
        lastIntervenedAt = now,
        cooldownUntil = cooldownDuration?.let { now.plus(it) } ?: cooldownUntil,
        updatedAt = now,
    )

    /** Adds a diagnosed blocker to known blockers if not already present. */
    fun withBlocker(blocker: String, now: Instant = Instant.now()): TrackedThread {
        val trimmed = blocker.trim()
        if (trimmed.isBlank() || trimmed in knownBlockers) return this
        return copy(
            knownBlockers = knownBlockers + trimmed,
            updatedAt = now,
        )
    }

    /** Updates the thread status. */
    fun withStatus(newStatus: ThreadStatus, now: Instant = Instant.now()): TrackedThread = copy(
        status = newStatus,
        updatedAt = now,
    )

    companion object {
        fun create(
            topic: String,
            title: String,
            now: Instant = Instant.now(),
            id: String = UUID.randomUUID().toString(),
            status: ThreadStatus = ThreadStatus.ACTIVE,
        ): TrackedThread = TrackedThread(
            id = id,
            topic = topic.trim(),
            title = title.trim(),
            status = status,
            consecutiveMisses = 0,
            consecutiveSuccesses = 0,
            knownBlockers = emptyList(),
            lastObservedAt = now,
            lastIntervenedAt = null,
            cooldownUntil = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
