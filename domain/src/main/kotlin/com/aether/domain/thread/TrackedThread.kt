package com.aether.domain.thread

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * An evolving habit, routine, or active life thread monitored by Aether.
 *
 * Rather than treating memories as isolated rows, threads track continuity
 * over time: consecutive successes, consecutive friction/misses, identified
 * blockers, active obstacle windows, and cooldown timers so the assistant
 * knows when to act and when to stay silent.
 */
data class TrackedThread(
    val id: String,
    val topic: String,
    val title: String,
    val status: ThreadStatus,
    val consecutiveMisses: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val knownBlockers: List<String> = emptyList(),
    val activeObstacle: ObstacleWindow? = null,
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

    /** Returns true if there is an active obstacle window that has not yet expired. */
    val isObstaclePaused: Boolean
        get() = activeObstacle != null && !activeObstacle.isExpired(lastObservedAt)

    /**
     * Checks if the active obstacle window has expired at [now] (inclusive boundary: now >= expiresAt).
     * If expired, resets consecutiveMisses to 0 for intervention triggering,
     * clears activeObstacle, but preserves immutable event history.
     */
    fun checkExpiration(now: Instant = Instant.now()): TrackedThread {
        val obstacle = activeObstacle ?: return this
        return if (obstacle.isExpired(now)) {
            copy(
                activeObstacle = null,
                consecutiveMisses = 0,
                updatedAt = now,
            )
        } else {
            this
        }
    }

    /**
     * Records a missed routine or friction event.
     * If an active obstacle is currently pausing the streak (now < expiresAt),
     * consecutiveMisses is preserved without increment.
     * If an obstacle window expired prior to this event (now >= expiresAt),
     * the stale streak is reset and this miss establishes consecutiveMisses = 1.
     */
    fun recordMiss(now: Instant = Instant.now()): TrackedThread {
        val refreshed = checkExpiration(now)
        return if (refreshed.activeObstacle != null) {
            // Obstacle is active and unexpired: streak remains paused
            refreshed.copy(
                lastObservedAt = now,
                updatedAt = now,
            )
        } else {
            // Normal active miss
            refreshed.copy(
                consecutiveMisses = refreshed.consecutiveMisses + 1,
                consecutiveSuccesses = 0,
                lastObservedAt = now,
                updatedAt = now,
            )
        }
    }

    /**
     * Records an external obstacle report according to [ObstacleRelationship].
     */
    fun recordObstacle(
        obstacleType: String,
        relationship: ObstacleRelationship,
        now: Instant = Instant.now(),
    ): TrackedThread {
        val refreshed = checkExpiration(now)
        val active = refreshed.activeObstacle

        return when {
            // 1. Same active episode: must match type AND be within active 48h window
            relationship == ObstacleRelationship.SAME_EPISODE &&
                    active != null &&
                    active.obstacleType.equals(obstacleType, ignoreCase = true) &&
                    !active.isExpired(now) -> {
                refreshed.copy(
                    activeObstacle = active.copy(observationCount = active.observationCount + 1),
                    lastObservedAt = now,
                    updatedAt = now,
                )
            }

            // 2. Expired continuation: strictly forbidden from reopening a window
            relationship == ObstacleRelationship.CONTINUATION_OF_EXPIRED_EPISODE ||
                    (active == null && relationship == ObstacleRelationship.SAME_EPISODE) -> {
                // Silently record observation in thread history; do NOT pause or alter misses
                refreshed.copy(
                    lastObservedAt = now,
                    updatedAt = now,
                )
            }

            // 3. Genuinely new episode: establishes fresh 48-hour window anchored at now
            relationship == ObstacleRelationship.NEW_EPISODE -> {
                refreshed.copy(
                    activeObstacle = ObstacleWindow(
                        obstacleType = obstacleType.trim().uppercase(),
                        startedAt = now,
                        expiresAt = now.plus(Duration.ofHours(48)),
                        observationCount = 1,
                    ),
                    lastObservedAt = now,
                    updatedAt = now,
                )
            }

            // 4. UNKNOWN / Ambiguous: Fail safe — record memory, but NEVER pause streak
            else -> {
                refreshed.copy(
                    lastObservedAt = now,
                    updatedAt = now,
                )
            }
        }
    }

    /** Records a successful completion of the routine, resetting miss streak and clearing obstacles. */
    fun recordSuccess(now: Instant = Instant.now()): TrackedThread = copy(
        activeObstacle = null,
        consecutiveSuccesses = consecutiveSuccesses + 1,
        consecutiveMisses = 0,
        lastObservedAt = now,
        updatedAt = now,
    )

    /** Records explicit recovery from an illness or obstacle ("fever is gone, hit workout"). */
    fun recordRecovery(now: Instant = Instant.now()): TrackedThread = copy(
        activeObstacle = null,
        consecutiveSuccesses = 1,
        consecutiveMisses = 0,
        status = ThreadStatus.ACTIVE,
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
            activeObstacle = null,
            lastObservedAt = now,
            lastIntervenedAt = null,
            cooldownUntil = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
