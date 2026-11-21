package com.aether.domain.thread

import java.time.Instant

/**
 * An immutable 48-hour obstacle window anchoring a temporary routine disruption.
 *
 * @param obstacleType Descriptive category (e.g. "FEVER", "TRAVEL", "CLOSED_FACILITY").
 * @param startedAt Immutable anchor timestamp T0 when the obstacle was first recorded.
 * @param expiresAt Fixed horizon: startedAt + 48 hours (inclusive: now >= expiresAt).
 * @param observationCount Number of user check-ins or observations recorded within this window.
 */
data class ObstacleWindow(
    val obstacleType: String,
    val startedAt: Instant,
    val expiresAt: Instant,
    val observationCount: Int = 1,
) {
    init {
        require(obstacleType.isNotBlank()) { "obstacleType must not be blank" }
        require(!expiresAt.isBefore(startedAt)) { "expiresAt must not be before startedAt" }
        require(observationCount >= 1) { "observationCount must be >= 1" }
    }

    /**
     * Checks if the obstacle window is expired at [now].
     * Contract: Inclusive boundary (now >= expiresAt).
     */
    fun isExpired(now: Instant): Boolean = now >= expiresAt
}

/**
 * Categorization of an incoming obstacle signal relative to past threads.
 */
enum class ObstacleRelationship {
    /** Confirmed ongoing observation of active obstacle (e.g., "still got the flu"). */
    SAME_EPISODE,

    /** A distinct, novel blocker (e.g., flu resolved, but now traveling or gym closed). */
    NEW_EPISODE,

    /** Refers to an already-expired obstacle (e.g., "still recovering from that flu last week"). */
    CONTINUATION_OF_EXPIRED_EPISODE,

    /** Ambiguous or uncertain attribution. Fails safe: NEVER creates or extends a pause window. */
    UNKNOWN,
}
