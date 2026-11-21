package com.aether.domain.policy

import java.time.Instant

/**
 * Result of forward eligibility projection.
 */
sealed interface EligibilityProjection {
    /**
     * Eligible right now (if [timestamp] <= now) or at a deterministically calculated
     * future point where all temporary binding temporal gates clear.
     */
    data class AvailableAt(val timestamp: Instant) : EligibilityProjection

    /**
     * Blocked by a condition that cannot clear automatically over time
     * (e.g. no concrete action provided or maximum consecutive dismissals reached).
     */
    data class Blocked(val reason: RestraintVerdict) : EligibilityProjection
}
