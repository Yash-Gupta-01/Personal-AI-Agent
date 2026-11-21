package com.aether.domain.policy

import com.aether.domain.action.InterventionCategory
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Standard implementation of [RestraintPolicy].
 *
 * Precedence Order:
 * 1. Specificity gate: [BLOCKED_NO_CONCRETE_ACTION]
 * 2. Daily budget: [BLOCKED_DAILY_BUDGET_EXHAUSTED]
 * 3. Quiet hours (supporting midnight-wrapping & same-day intervals): [BLOCKED_QUIET_HOURS]
 * 4. Active thread cooldown: [BLOCKED_ACTIVE_COOLDOWN]
 * 5. Dismissal threshold: [BLOCKED_REPEATED_DISMISSALS]
 */
class DefaultRestraintPolicy : RestraintPolicy {

    override fun evaluate(
        now: Instant,
        zone: ZoneId,
        todayInterventionCount: Int,
        threadCooldownUntil: Instant?,
        consecutiveDismissals: Int,
        hasConcreteAction: Boolean,
        config: RestraintConfig,
    ): RestraintVerdict {
        // 1. Specificity gate: must propose a concrete tool action, never generic chit-chat
        if (!hasConcreteAction) {
            return RestraintVerdict.BLOCKED_NO_CONCRETE_ACTION
        }

        // 2. Daily interruption budget (e.g. max 1 nudge per day)
        if (todayInterventionCount >= config.maxDailyInterventions) {
            return RestraintVerdict.BLOCKED_DAILY_BUDGET_EXHAUSTED
        }

        // 3. Quiet hours check (if enabled by user)
        if (config.quietHoursEnabled) {
            val localTime = now.atZone(zone).toLocalTime()
            if (isQuietHours(localTime, config.quietHoursStart, config.quietHoursEnd)) {
                return RestraintVerdict.BLOCKED_QUIET_HOURS
            }
        }

        // 4. Active cooldown on this thread
        if (threadCooldownUntil != null && now.isBefore(threadCooldownUntil)) {
            return RestraintVerdict.BLOCKED_ACTIVE_COOLDOWN
        }

        // 5. Dismissal learning (auto-mute after repeated dismissals)
        if (consecutiveDismissals >= config.maxConsecutiveDismissalsBeforeCooldown) {
            return RestraintVerdict.BLOCKED_REPEATED_DISMISSALS
        }

        return RestraintVerdict.PERMITTED
    }

    override fun projectEligibility(
        now: Instant,
        zone: ZoneId,
        hasConcreteAction: Boolean,
        category: InterventionCategory,
        interventionsInCircadianWindow: Int,
        lastPostedAt: Instant?,
        threadCooldownUntil: Instant?,
        consecutiveDismissals: Int,
        config: RestraintConfig,
    ): EligibilityProjection {
        // 1. Non-temporal blocking conditions fail immediately with Blocked(reason)
        if (!hasConcreteAction) {
            return EligibilityProjection.Blocked(RestraintVerdict.BLOCKED_NO_CONCRETE_ACTION)
        }
        if (consecutiveDismissals >= config.maxConsecutiveDismissalsBeforeCooldown) {
            return EligibilityProjection.Blocked(RestraintVerdict.BLOCKED_REPEATED_DISMISSALS)
        }

        // 2. Solicited user commands bypass proactive constraints entirely
        if (category == InterventionCategory.SOLICITED) {
            return EligibilityProjection.AvailableAt(now)
        }

        // 3. Proactive intervention evaluation
        val isBudgetFree = interventionsInCircadianWindow < config.maxDailyInterventions
        val isCooldownClear = lastPostedAt == null || now >= lastPostedAt.plus(COOLDOWN_DURATION)
        val isQuietClear = !config.quietHoursEnabled || !isQuietHours(now.atZone(zone).toLocalTime(), config.quietHoursStart, config.quietHoursEnd)
        val isThreadClear = threadCooldownUntil == null || now >= threadCooldownUntil

        // Immediate availability check
        if (isBudgetFree && isCooldownClear && isQuietClear && isThreadClear) {
            return EligibilityProjection.AvailableAt(now)
        }

        // 4. Calculate exact clearance timestamp for each active temporal restriction
        val candidateTimes = mutableListOf<Instant>()

        if (!isBudgetFree) {
            // Wait for 04:00 AM boundary of the next circadian day
            candidateTimes.add(CircadianWindow.of(now, zone).end)
        }
        if (!isCooldownClear) {
            candidateTimes.add(lastPostedAt!!.plus(COOLDOWN_DURATION))
        }
        if (!isQuietClear) {
            candidateTimes.add(nextQuietHoursEnd(now, zone, config.quietHoursStart, config.quietHoursEnd))
        }
        if (!isThreadClear) {
            candidateTimes.add(threadCooldownUntil!!)
        }

        var target = candidateTimes.maxOrNull() ?: now

        // If projected target lands inside quiet hours, snap forward to quiet hours end
        if (config.quietHoursEnabled && isQuietHours(target.atZone(zone).toLocalTime(), config.quietHoursStart, config.quietHoursEnd)) {
            target = nextQuietHoursEnd(target, zone, config.quietHoursStart, config.quietHoursEnd)
        }

        return EligibilityProjection.AvailableAt(target)
    }

    companion object {
        val COOLDOWN_DURATION: Duration = Duration.ofHours(10)

        /**
         * Determines if [time] falls in the window [start] (inclusive) to [end] (exclusive).
         * Supports both same-day intervals (e.g. 13:00 to 15:00) and
         * midnight-wrapping intervals (e.g. 22:00 to 07:00).
         */
        fun isQuietHours(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
            if (start == end) return false
            return if (start > end) {
                // Midnight-wrapping: [start, 23:59:59.999] or [00:00, end)
                time >= start || time < end
            } else {
                // Same-day: [start, end)
                time >= start && time < end
            }
        }

        /**
         * Computes the exact timestamp when quiet hours end relative to [instant] in [zone].
         */
        fun nextQuietHoursEnd(instant: Instant, zone: ZoneId, start: LocalTime, end: LocalTime): Instant {
            val zdt = instant.atZone(zone)
            val localTime = zdt.toLocalTime()
            val date = zdt.toLocalDate()

            val endZdt = if (start > end) {
                // Midnight-wrapping (e.g. 22:00 to 07:00)
                if (localTime >= start) {
                    ZonedDateTime.of(date.plusDays(1), end, zone)
                } else {
                    ZonedDateTime.of(date, end, zone)
                }
            } else {
                // Same-day (e.g. 13:00 to 15:00)
                if (localTime >= end) {
                    ZonedDateTime.of(date.plusDays(1), end, zone)
                } else {
                    ZonedDateTime.of(date, end, zone)
                }
            }
            return endZdt.toInstant()
        }
    }
}
