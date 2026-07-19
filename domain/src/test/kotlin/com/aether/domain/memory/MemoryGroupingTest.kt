package com.aether.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MemoryGroupingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun memoryAt(isoInstant: String, content: String) =
        Memory.capture(content, MemorySource.MANUAL_TEXT, Instant.parse(isoInstant))

    @Test
    fun `empty input produces no days`() {
        assertTrue(emptyList<Memory>().groupByDay(zone).isEmpty())
    }

    @Test
    fun `memories on the same local day share a group`() {
        val memories = listOf(
            memoryAt("2026-07-19T04:00:00Z", "Morning"),
            memoryAt("2026-07-19T14:00:00Z", "Evening"),
        )

        val days = memories.groupByDay(zone)

        assertEquals(1, days.size)
        assertEquals(2, days.single().memories.size)
    }

    @Test
    fun `days are ordered newest first`() {
        val memories = listOf(
            memoryAt("2026-07-17T06:00:00Z", "Oldest"),
            memoryAt("2026-07-19T06:00:00Z", "Newest"),
            memoryAt("2026-07-18T06:00:00Z", "Middle"),
        )

        val dates = memories.groupByDay(zone).map { it.date }

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 18),
                LocalDate.of(2026, 7, 17),
            ),
            dates,
        )
    }

    @Test
    fun `memories within a day are ordered newest first`() {
        val memories = listOf(
            memoryAt("2026-07-19T04:00:00Z", "First"),
            memoryAt("2026-07-19T14:00:00Z", "Last"),
        )

        val contents = memories.groupByDay(zone).single().memories.map { it.content }

        assertEquals(listOf("Last", "First"), contents)
    }

    @Test
    fun `day boundaries follow the local zone not UTC`() {
        // 22:00 UTC on the 18th is 03:30 on the 19th in Kolkata. A thought
        // captured late at night must land on the day the user experienced,
        // otherwise the dashboard disagrees with the user's memory of it.
        val lateNight = memoryAt("2026-07-18T22:00:00Z", "Late thought")

        val date = listOf(lateNight).groupByDay(zone).single().date

        assertEquals(LocalDate.of(2026, 7, 19), date)
    }
}
