package com.aether.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoryTest {

    private val now: Instant = Instant.parse("2026-07-19T10:15:30Z")

    @Test
    fun `capture starts unclassified and active`() {
        val memory = Memory.capture("Call the dentist", MemorySource.MANUAL_TEXT, now)

        // The user is never asked to categorise at capture time — that is the
        // memory agent's job, so everything must start unclassified.
        assertEquals(MemoryType.UNCLASSIFIED, memory.type)
        assertEquals(MemoryStatus.ACTIVE, memory.status)
    }

    @Test
    fun `capture sets createdAt and updatedAt to the same instant`() {
        val memory = Memory.capture("Buy milk", MemorySource.MANUAL_TEXT, now)

        assertEquals(now, memory.createdAt)
        assertEquals(now, memory.updatedAt)
    }

    @Test
    fun `capture trims surrounding whitespace`() {
        val memory = Memory.capture("   Book flights   ", MemorySource.MANUAL_TEXT, now)

        assertEquals("Book flights", memory.content)
    }

    @Test
    fun `capture generates a unique id per memory`() {
        val first = Memory.capture("One", MemorySource.MANUAL_TEXT, now)
        val second = Memory.capture("Two", MemorySource.MANUAL_TEXT, now)

        assertTrue(first.id.isNotBlank())
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `blank content is rejected`() {
        // Guards the trust boundary: a mis-fired voice capture must not be
        // able to write an empty row that then shows as a blank dashboard card.
        assertThrows(IllegalArgumentException::class.java) {
            Memory.capture("   ", MemorySource.MANUAL_VOICE, now)
        }
    }

    @Test
    fun `withContent advances updatedAt but preserves createdAt and id`() {
        val original = Memory.capture("Draft", MemorySource.MANUAL_TEXT, now)
        val later = now.plusSeconds(3600)

        val edited = original.withContent("Final", later)

        assertEquals("Final", edited.content)
        assertEquals(later, edited.updatedAt)
        assertEquals(now, edited.createdAt)
        assertEquals(original.id, edited.id)
    }

    @Test
    fun `voice captures record their source`() {
        val memory = Memory.capture("Remember to stretch", MemorySource.MANUAL_VOICE, now)

        // Provenance matters: voice transcripts carry recognition errors that
        // typed text does not.
        assertEquals(MemorySource.MANUAL_VOICE, memory.source)
    }
}
