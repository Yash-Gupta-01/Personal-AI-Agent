package com.aether.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryClassifierTest {

    @Test
    fun `prompt offers every real type`() {
        val prompt = MemoryClassifier.buildPrompt("anything")

        // Generated from the enum, so adding a MemoryType cannot silently
        // leave the prompt stale. This test fails loudly if that link breaks.
        MemoryType.entries
            .filterNot { it == MemoryType.UNCLASSIFIED }
            .forEach { type ->
                assertTrue("prompt is missing ${type.name}", prompt.contains(type.name))
            }
    }

    @Test
    fun `prompt does not offer UNCLASSIFIED as a choice`() {
        // UNCLASSIFIED is the absence of a decision, not something to pick.
        assertFalse(MemoryClassifier.buildPrompt("anything").contains("UNCLASSIFIED"))
    }

    @Test
    fun `prompt includes the note being classified`() {
        assertTrue(MemoryClassifier.buildPrompt("Call the dentist").contains("Call the dentist"))
    }

    @Test
    fun `exact label parses`() {
        assertEquals(MemoryType.HABIT, MemoryClassifier.parse("HABIT"))
    }

    @Test
    fun `lowercase label parses`() {
        assertEquals(MemoryType.GOAL, MemoryClassifier.parse("goal"))
    }

    @Test
    fun `surrounding whitespace and newlines are tolerated`() {
        assertEquals(MemoryType.CAREER, MemoryClassifier.parse("  \n CAREER \n "))
    }

    @Test
    fun `trailing punctuation is tolerated`() {
        assertEquals(MemoryType.HEALTH, MemoryClassifier.parse("HEALTH."))
    }

    @Test
    fun `echoed prompt prefix is stripped`() {
        // Small models frequently repeat the prompt's last line before answering.
        assertEquals(MemoryType.PROJECT, MemoryClassifier.parse("Category: PROJECT"))
    }

    @Test
    fun `label buried in a sentence is found`() {
        assertEquals(
            MemoryType.RELATIONSHIP,
            MemoryClassifier.parse("The best fit here is RELATIONSHIP"),
        )
    }

    @Test
    fun `unknown label falls back to unclassified`() {
        // Leaving it unclassified keeps it in the queue. Guessing would produce
        // a wrong label that nothing downstream could detect as wrong.
        assertEquals(MemoryType.UNCLASSIFIED, MemoryClassifier.parse("SHOPPING"))
    }

    @Test
    fun `empty output falls back to unclassified`() {
        assertEquals(MemoryType.UNCLASSIFIED, MemoryClassifier.parse(""))
    }

    @Test
    fun `whitespace-only output falls back to unclassified`() {
        assertEquals(MemoryType.UNCLASSIFIED, MemoryClassifier.parse("   \n\t "))
    }

    @Test
    fun `punctuation-only output falls back to unclassified`() {
        assertEquals(MemoryType.UNCLASSIFIED, MemoryClassifier.parse("!!! ... ???"))
    }

    @Test
    fun `output naming two categories is treated as unclassified`() {
        // Genuinely ambiguous. Picking one would be a coin flip recorded as fact.
        assertEquals(
            MemoryType.UNCLASSIFIED,
            MemoryClassifier.parse("could be GOAL or maybe CAREER"),
        )
    }

    @Test
    fun `first word wins when the model answers then rambles`() {
        // The common well-behaved-but-chatty case: correct answer, then noise.
        assertEquals(
            MemoryType.KNOWLEDGE,
            MemoryClassifier.parse("KNOWLEDGE because it records a fact"),
        )
    }

    @Test
    fun `every type round-trips through parse`() {
        MemoryType.entries
            .filterNot { it == MemoryType.UNCLASSIFIED }
            .forEach { type ->
                assertEquals(type, MemoryClassifier.parse(type.name))
            }
    }
}
