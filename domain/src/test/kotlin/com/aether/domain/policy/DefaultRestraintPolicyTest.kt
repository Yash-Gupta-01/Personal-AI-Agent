package com.aether.domain.policy

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class DefaultRestraintPolicyTest {

    private val policy = DefaultRestraintPolicy()
    private val utcZone = ZoneOffset.UTC

    private fun instantAt(time: LocalTime, date: LocalDate = LocalDate.of(2026, 11, 21)): Instant =
        date.atTime(time).toInstant(ZoneOffset.UTC)

    @Test
    fun `midnight-wrapping quiet hours - 21_59 permitted`() {
        val now = instantAt(LocalTime.of(21, 59))
        val verdict = policy.evaluate(
            now = now,
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            ),
        )
        assertEquals(RestraintVerdict.PERMITTED, verdict)
    }

    @Test
    fun `midnight-wrapping quiet hours - 22_00 boundary is blocked`() {
        val now = instantAt(LocalTime.of(22, 0))
        val verdict = policy.evaluate(
            now = now,
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            ),
        )
        assertEquals(RestraintVerdict.BLOCKED_QUIET_HOURS, verdict)
    }

    @Test
    fun `midnight-wrapping quiet hours - 02_30 deep night is blocked`() {
        val now = instantAt(LocalTime.of(2, 30))
        val verdict = policy.evaluate(
            now = now,
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            ),
        )
        assertEquals(RestraintVerdict.BLOCKED_QUIET_HOURS, verdict)
    }

    @Test
    fun `midnight-wrapping quiet hours - 07_00 morning boundary is permitted`() {
        val now = instantAt(LocalTime.of(7, 0))
        val verdict = policy.evaluate(
            now = now,
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            ),
        )
        assertEquals(RestraintVerdict.PERMITTED, verdict)
    }

    @Test
    fun `same-day quiet hours - 13_00 to 15_00 afternoon window`() {
        val config = RestraintConfig(
            quietHoursStart = LocalTime.of(13, 0),
            quietHoursEnd = LocalTime.of(15, 0),
        )

        // 12:59 - permitted
        assertEquals(
            RestraintVerdict.PERMITTED,
            policy.evaluate(
                now = instantAt(LocalTime.of(12, 59)),
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )

        // 13:00 - blocked
        assertEquals(
            RestraintVerdict.BLOCKED_QUIET_HOURS,
            policy.evaluate(
                now = instantAt(LocalTime.of(13, 0)),
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )

        // 14:30 - blocked
        assertEquals(
            RestraintVerdict.BLOCKED_QUIET_HOURS,
            policy.evaluate(
                now = instantAt(LocalTime.of(14, 30)),
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )

        // 15:00 - permitted
        assertEquals(
            RestraintVerdict.PERMITTED,
            policy.evaluate(
                now = instantAt(LocalTime.of(15, 0)),
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )
    }

    @Test
    fun `same start and end quiet hours disables quiet window`() {
        val config = RestraintConfig(
            quietHoursStart = LocalTime.of(12, 0),
            quietHoursEnd = LocalTime.of(12, 0),
        )
        val verdict = policy.evaluate(
            now = instantAt(LocalTime.of(12, 0)),
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = config,
        )
        assertEquals(RestraintVerdict.PERMITTED, verdict)
    }

    @Test
    fun `daily interruption budget enforcement`() {
        val daytime = instantAt(LocalTime.of(10, 0))
        val config = RestraintConfig(maxDailyInterventions = 1)

        // 0 interventions today: permitted
        assertEquals(
            RestraintVerdict.PERMITTED,
            policy.evaluate(
                now = daytime,
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )

        // 1 intervention today: budget exhausted
        assertEquals(
            RestraintVerdict.BLOCKED_DAILY_BUDGET_EXHAUSTED,
            policy.evaluate(
                now = daytime,
                zone = utcZone,
                todayInterventionCount = 1,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )

        // 2 interventions today: budget exhausted
        assertEquals(
            RestraintVerdict.BLOCKED_DAILY_BUDGET_EXHAUSTED,
            policy.evaluate(
                now = daytime,
                zone = utcZone,
                todayInterventionCount = 2,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
                config = config,
            ),
        )
    }

    @Test
    fun `active cooldown timestamp boundary`() {
        val daytime = instantAt(LocalTime.of(10, 0))
        val cooldownUntil = daytime.plusSeconds(3600) // 11:00

        // Before cooldown expires: blocked
        assertEquals(
            RestraintVerdict.BLOCKED_ACTIVE_COOLDOWN,
            policy.evaluate(
                now = daytime,
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = cooldownUntil,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
            ),
        )

        // Exactly at cooldown timestamp: permitted (cooldown expired)
        assertEquals(
            RestraintVerdict.PERMITTED,
            policy.evaluate(
                now = cooldownUntil,
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = cooldownUntil,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
            ),
        )

        // After cooldown timestamp: permitted
        assertEquals(
            RestraintVerdict.PERMITTED,
            policy.evaluate(
                now = cooldownUntil.plusSeconds(60),
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = cooldownUntil,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
            ),
        )
    }

    @Test
    fun `consecutive dismissals threshold`() {
        val daytime = instantAt(LocalTime.of(10, 0))
        val config = RestraintConfig(maxConsecutiveDismissalsBeforeCooldown = 3)

        // 2 dismissals: permitted
        assertEquals(
            RestraintVerdict.PERMITTED,
            policy.evaluate(
                now = daytime,
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 2,
                hasConcreteAction = true,
                config = config,
            ),
        )

        // 3 dismissals: blocked
        assertEquals(
            RestraintVerdict.BLOCKED_REPEATED_DISMISSALS,
            policy.evaluate(
                now = daytime,
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = null,
                consecutiveDismissals = 3,
                hasConcreteAction = true,
                config = config,
            ),
        )
    }

    @Test
    fun `specificity gate blocks intervention when hasConcreteAction is false`() {
        val daytime = instantAt(LocalTime.of(10, 0))
        val verdict = policy.evaluate(
            now = daytime,
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = false,
        )
        assertEquals(RestraintVerdict.BLOCKED_NO_CONCRETE_ACTION, verdict)
    }

    @Test
    fun `precedence order when multiple blockers apply`() {
        val nightTime = instantAt(LocalTime.of(23, 0))
        val cooldownUntil = nightTime.plusSeconds(3600)

        // 1. Specificity gate takes precedence over budget
        assertEquals(
            RestraintVerdict.BLOCKED_NO_CONCRETE_ACTION,
            policy.evaluate(
                now = instantAt(LocalTime.of(10, 0)),
                zone = utcZone,
                todayInterventionCount = 5,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = false,
            ),
        )

        // 2. Daily budget takes precedence over quiet hours
        assertEquals(
            RestraintVerdict.BLOCKED_DAILY_BUDGET_EXHAUSTED,
            policy.evaluate(
                now = nightTime,
                zone = utcZone,
                todayInterventionCount = 1,
                threadCooldownUntil = null,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
            ),
        )

        // 3. Quiet hours takes precedence over active cooldown
        assertEquals(
            RestraintVerdict.BLOCKED_QUIET_HOURS,
            policy.evaluate(
                now = nightTime,
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = cooldownUntil,
                consecutiveDismissals = 0,
                hasConcreteAction = true,
            ),
        )

        // 4. Cooldown takes precedence over repeated dismissals
        assertEquals(
            RestraintVerdict.BLOCKED_ACTIVE_COOLDOWN,
            policy.evaluate(
                now = instantAt(LocalTime.of(10, 0)),
                zone = utcZone,
                todayInterventionCount = 0,
                threadCooldownUntil = instantAt(LocalTime.of(12, 0)),
                consecutiveDismissals = 10,
                hasConcreteAction = true,
            ),
        )
    }

    @Test
    fun `quiet hours disabled by user permits intervention even at 02_30 night`() {
        val now = instantAt(LocalTime.of(2, 30))
        val verdict = policy.evaluate(
            now = now,
            zone = utcZone,
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(
                quietHoursEnabled = false,
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            ),
        )
        assertEquals(RestraintVerdict.PERMITTED, verdict)
    }
}
