package com.aether.domain.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ActionRecordTest {

    private val fixedNow = Instant.parse("2026-11-21T10:00:00Z")

    private fun sampleAction(status: ActionStatus = ActionStatus.PROPOSED): ActionRecord =
        ActionRecord.propose(
            threadId = "thread-gym",
            toolName = "set_alarm",
            toolParams = mapOf("hour" to "7", "minute" to "0"),
            now = fixedNow,
            id = "act-1",
        ).let {
            if (status != ActionStatus.PROPOSED) {
                it.copy(status = status)
            } else {
                it
            }
        }

    @Test
    fun `proposed action permits legal transitions`() {
        val proposed = sampleAction(ActionStatus.PROPOSED)
        assertTrue(proposed.canTransitionTo(ActionStatus.APPROVED))
        assertTrue(proposed.canTransitionTo(ActionStatus.EXECUTED))
        assertTrue(proposed.canTransitionTo(ActionStatus.DISMISSED))
        assertTrue(proposed.canTransitionTo(ActionStatus.EXPIRED))
        assertTrue(proposed.canTransitionTo(ActionStatus.FAILED))

        assertFalse(proposed.canTransitionTo(ActionStatus.OBSERVED))
        assertFalse(proposed.canTransitionTo(ActionStatus.EVALUATED))
    }

    @Test
    fun `approved action permits execution or cancellation`() {
        val approved = sampleAction(ActionStatus.APPROVED)
        assertTrue(approved.canTransitionTo(ActionStatus.EXECUTED))
        assertTrue(approved.canTransitionTo(ActionStatus.DISMISSED))
        assertTrue(approved.canTransitionTo(ActionStatus.EXPIRED))
        assertTrue(approved.canTransitionTo(ActionStatus.FAILED))

        assertFalse(approved.canTransitionTo(ActionStatus.PROPOSED))
        assertFalse(approved.canTransitionTo(ActionStatus.OBSERVED))
    }

    @Test
    fun `executed action can transition to observed, evaluated, or failed`() {
        val executed = sampleAction(ActionStatus.EXECUTED)
        assertTrue(executed.canTransitionTo(ActionStatus.OBSERVED))
        assertTrue(executed.canTransitionTo(ActionStatus.EVALUATED))
        assertTrue(executed.canTransitionTo(ActionStatus.FAILED))

        assertFalse(executed.canTransitionTo(ActionStatus.PROPOSED))
        assertFalse(executed.canTransitionTo(ActionStatus.APPROVED))
        assertFalse(executed.canTransitionTo(ActionStatus.DISMISSED))
    }

    @Test
    fun `observed action can transition to evaluated or expired`() {
        val observed = sampleAction(ActionStatus.OBSERVED)
        assertTrue(observed.canTransitionTo(ActionStatus.EVALUATED))
        assertTrue(observed.canTransitionTo(ActionStatus.EXPIRED))

        assertFalse(observed.canTransitionTo(ActionStatus.EXECUTED))
        assertFalse(observed.canTransitionTo(ActionStatus.PROPOSED))
    }

    @Test
    fun `terminal states reject all transitions`() {
        val terminalStates = listOf(
            ActionStatus.EVALUATED,
            ActionStatus.DISMISSED,
            ActionStatus.EXPIRED,
            ActionStatus.FAILED,
        )

        for (terminal in terminalStates) {
            val record = sampleAction(terminal)
            for (target in ActionStatus.entries) {
                assertFalse("Expected $terminal to reject transition to $target", record.canTransitionTo(target))
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `transitionTo throws on illegal transition from dismissed to executed`() {
        val dismissed = sampleAction(ActionStatus.DISMISSED)
        dismissed.transitionTo(ActionStatus.EXECUTED)
    }

    @Test(expected = IllegalStateException::class)
    fun `transitionTo throws on illegal transition from expired to executed`() {
        val expired = sampleAction(ActionStatus.EXPIRED)
        expired.transitionTo(ActionStatus.EXECUTED)
    }

    @Test(expected = IllegalStateException::class)
    fun `transitionTo throws on illegal transition from failed to executed`() {
        val failed = sampleAction(ActionStatus.FAILED)
        failed.transitionTo(ActionStatus.EXECUTED)
    }

    @Test
    fun `transition to executed sets executedAt and advances updatedAt`() {
        val proposed = sampleAction(ActionStatus.PROPOSED)
        val executeTime = fixedNow.plusSeconds(300)
        val followUpDue = executeTime.plusSeconds(5400)

        val executed = proposed.transitionTo(
            newStatus = ActionStatus.EXECUTED,
            now = executeTime,
            followUpDueAt = followUpDue,
        )

        assertEquals(ActionStatus.EXECUTED, executed.status)
        assertEquals(executeTime, executed.executedAt)
        assertEquals(followUpDue, executed.followUpDueAt)
        assertEquals(executeTime, executed.updatedAt)
    }

    @Test
    fun `transition to evaluated records outcome note`() {
        val executed = sampleAction(ActionStatus.EXECUTED)
        val evalTime = fixedNow.plusSeconds(3600)
        val note = "Closed alarm due to lack of sleep"

        val evaluated = executed.transitionTo(
            newStatus = ActionStatus.EVALUATED,
            now = evalTime,
            outcomeNote = note,
        )

        assertEquals(ActionStatus.EVALUATED, evaluated.status)
        assertEquals(note, evaluated.outcomeNote)
        assertEquals(evalTime, evaluated.updatedAt)
    }
}
