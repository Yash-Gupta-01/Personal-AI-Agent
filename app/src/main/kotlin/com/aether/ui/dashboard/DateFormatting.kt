package com.aether.ui.dashboard

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Human day labels for the dashboard.
 *
 * "Today" and "Yesterday" instead of dates: those are the two days you actually
 * think in, and reading a date to work out it means today is exactly the small
 * cognitive tax Aether exists to remove.
 */
internal fun LocalDate.toDayLabel(
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault(),
): String = when (this) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> {
        val pattern = if (year == today.year) "EEEE, d MMMM" else "d MMMM yyyy"
        format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

/** Capture time, shown on each memory so the day's sequence is legible. */
internal fun Instant.toTimeLabel(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", locale))
