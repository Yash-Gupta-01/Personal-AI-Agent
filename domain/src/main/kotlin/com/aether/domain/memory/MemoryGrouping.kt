package com.aether.domain.memory

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A day's worth of memories, as shown on the dashboard.
 *
 * Grouping lives in the domain rather than the UI because "which day is this
 * memory from" is a rule about memories, not about pixels — and because it is
 * far cheaper to test here than through a Compose test.
 */
data class MemoryDay(
    val date: LocalDate,
    val memories: List<Memory>,
)

/**
 * Groups memories into days, newest day first and newest memory first within
 * each day.
 *
 * [zone] is a parameter rather than read from the system so tests are not at
 * the mercy of the machine's timezone. Day boundaries are local: a thought
 * captured at 11pm belongs to today, not to tomorrow in UTC.
 */
fun List<Memory>.groupByDay(zone: ZoneId = ZoneId.systemDefault()): List<MemoryDay> =
    sortedByDescending { it.createdAt }
        .groupBy { it.createdAt.toLocalDate(zone) }
        .map { (date, memories) -> MemoryDay(date, memories) }
        .sortedByDescending { it.date }

private fun Instant.toLocalDate(zone: ZoneId): LocalDate =
    atZone(zone).toLocalDate()
