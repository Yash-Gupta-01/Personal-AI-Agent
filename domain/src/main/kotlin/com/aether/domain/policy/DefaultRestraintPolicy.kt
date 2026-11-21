package com.aether.domain.policy

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

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

        // 3. Quiet hours check
        val localTime = now.atZone(zone).toLocalTime()
        if (isQuietHours(localTime, config.quietHoursStart, config.quietHoursEnd)) {
            return RestraintVerdict.BLOCKED_QUIET_HOURS
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

    companion object {
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
    }
}
