package com.aether.domain.policy

import com.aether.domain.action.InterventionCategory
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class RestraintConfig(
    val maxDailyInterventions: Int = 1,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: LocalTime = LocalTime.of(22, 0),
    val quietHoursEnd: LocalTime = LocalTime.of(7, 0),
    val maxConsecutiveDismissalsBeforeCooldown: Int = 3,
)

enum class RestraintVerdict {
    /** Intervening now is permitted by policy. */
    PERMITTED,

    /** Blocked: No concrete device action or suggestion is attached. */
    BLOCKED_NO_CONCRETE_ACTION,

    /** Blocked: Daily interruption budget has been exhausted. */
    BLOCKED_DAILY_BUDGET_EXHAUSTED,

    /** Blocked: Current local time falls within configured quiet hours. */
    BLOCKED_QUIET_HOURS,

    /** Blocked: Thread or global intervention is under active cooldown backoff. */
    BLOCKED_ACTIVE_COOLDOWN,

    /** Blocked: Repeated dismissals triggered automatic quiet mode. */
    BLOCKED_REPEATED_DISMISSALS,
}

/**
 * Domain policy enforcing silence over noise.
 *
 * Restraint is a first-class domain invariant: Aether strictly limits
 * interruptions, honors quiet hours, respects backoff timers, and never
 * interrupts without a concrete, high-leverage action.
 */
interface RestraintPolicy {
    fun evaluate(
        now: Instant,
        zone: ZoneId,
        todayInterventionCount: Int,
        threadCooldownUntil: Instant?,
        consecutiveDismissals: Int,
        hasConcreteAction: Boolean,
        config: RestraintConfig = RestraintConfig(),
    ): RestraintVerdict

    /**
     * Deterministically projects when an intervention will next be eligible
     * without arbitrary polling loops.
     */
    fun projectEligibility(
        now: Instant,
        zone: ZoneId,
        hasConcreteAction: Boolean,
        category: InterventionCategory,
        interventionsInCircadianWindow: Int,
        lastPostedAt: Instant?,
        threadCooldownUntil: Instant?,
        consecutiveDismissals: Int,
        config: RestraintConfig = RestraintConfig(),
    ): EligibilityProjection
}
