package com.aether.domain.policy

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Models the circadian day anchored at 04:00 AM device local time.
 *
 * Any timestamp between [04:00:00 today, 03:59:59.999 tomorrow) belongs
 * to the same circadian day.
 */
data class CircadianWindow(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
) {
    init {
        require(!end.isBefore(start)) { "Circadian end must not be before start" }
    }

    /** Returns true if [instant] falls within this circadian day window. */
    fun contains(instant: Instant): Boolean = instant >= start && instant < end

    companion object {
        val CIRCADIAN_RESET_TIME: LocalTime = LocalTime.of(4, 0)

        /**
         * Resolves the circadian window for a given [instant] in [zone].
         * If local time is before 04:00 AM, it belongs to the previous calendar day's circadian cycle.
         */
        fun of(instant: Instant, zone: ZoneId): CircadianWindow {
            val zdt = instant.atZone(zone)
            val localTime = zdt.toLocalTime()
            val circadianDate = if (localTime < CIRCADIAN_RESET_TIME) {
                zdt.toLocalDate().minusDays(1)
            } else {
                zdt.toLocalDate()
            }

            val startZdt = ZonedDateTime.of(circadianDate, CIRCADIAN_RESET_TIME, zone)
            val endZdt = startZdt.plusDays(1)

            return CircadianWindow(
                date = circadianDate,
                start = startZdt.toInstant(),
                end = endZdt.toInstant(),
            )
        }
    }
}
