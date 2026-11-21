package com.aether.data.action

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.data.AetherDatabase
import com.aether.data.privacy.RoomPiiRedactor
import com.aether.data.thread.RoomTrackedThreadRepository
import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionStatus
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.ThreadStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Exercises the ambient loop persistence layer on the real device SQLite.
 */
@RunWith(AndroidJUnit4::class)
class ActionDaoTest {

    private lateinit var database: AetherDatabase
    private lateinit var threadRepository: RoomTrackedThreadRepository
    private lateinit var actionRepository: RoomActionRecordRepository
    private lateinit var piiRedactor: RoomPiiRedactor

    private val now = Instant.parse("2026-11-21T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AetherDatabase::class.java,
        ).build()
        threadRepository = RoomTrackedThreadRepository(database.trackedThreadDao())
        actionRepository = RoomActionRecordRepository(database.actionRecordDao())
        piiRedactor = RoomPiiRedactor(database.piiEntityMapDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun trackedThreadLifecycleRoundTrip() = runTest {
        val thread = TrackedThread(
            id = "thread-gym-1",
            topic = "gym",
            title = "Morning Gym Routine",
            status = ThreadStatus.ACTIVE,
            consecutiveMisses = 2,
            consecutiveSuccesses = 0,
            knownBlockers = emptyList(),
            lastObservedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        threadRepository.save(thread)

        val retrieved = threadRepository.getByTopic("gym")
        assertNotNull(retrieved)
        assertEquals("thread-gym-1", retrieved!!.id)
        assertEquals("Morning Gym Routine", retrieved.title)
        assertEquals(2, retrieved.consecutiveMisses)

        // Attach blocker
        val updated = retrieved.withBlocker("sleep_debt", now.plusSeconds(3600))
        threadRepository.save(updated)

        val retrievedWithBlocker = threadRepository.getById("thread-gym-1")
        assertNotNull(retrievedWithBlocker)
        assertEquals(listOf("sleep_debt"), retrievedWithBlocker!!.knownBlockers)
    }

    @Test
    fun actionRecordStateTransitionsPersistCorrectly() = runTest {
        val thread = TrackedThread(
            id = "thread-gym-1",
            topic = "gym",
            title = "Morning Gym Routine",
            status = ThreadStatus.ACTIVE,
            consecutiveMisses = 1,
            consecutiveSuccesses = 0,
            knownBlockers = emptyList(),
            lastObservedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        threadRepository.save(thread)

        val action = ActionRecord.propose(
            threadId = "thread-gym-1",
            toolName = "set_alarm",
            toolParams = mapOf("hour" to "7", "minute" to "0", "label" to "Gym"),
            now = now,
        )
        actionRepository.save(action)

        val proposed = actionRepository.getById(action.id)
        assertNotNull(proposed)
        assertEquals(ActionStatus.PROPOSED, proposed!!.status)

        // Execute
        val executedTime = now.plusSeconds(60)
        val followUpDue = now.plusSeconds(5400)
        val executed = proposed.transitionTo(
            newStatus = ActionStatus.EXECUTED,
            now = executedTime,
            followUpDueAt = followUpDue,
        )
        actionRepository.save(executed)

        val retrievedExecuted = actionRepository.getById(action.id)
        assertNotNull(retrievedExecuted)
        assertEquals(ActionStatus.EXECUTED, retrievedExecuted!!.status)
        assertEquals(executedTime, retrievedExecuted.executedAt)
        assertEquals(followUpDue, retrievedExecuted.followUpDueAt)

        // Follow-up evaluation with RemoteInput reply
        val evalTime = now.plusSeconds(5450)
        val evaluated = retrievedExecuted.transitionTo(
            newStatus = ActionStatus.EVALUATED,
            now = evalTime,
            outcomeNote = "closed it due to lack of sleep",
        )
        actionRepository.save(evaluated)

        val retrievedEvaluated = actionRepository.getById(action.id)
        assertNotNull(retrievedEvaluated)
        assertEquals(ActionStatus.EVALUATED, retrievedEvaluated!!.status)
        assertEquals("closed it due to lack of sleep", retrievedEvaluated.outcomeNote)
    }

    @Test
    fun piiRedactorDeterministicMaskingAndRehydration() = runTest {
        val original = "I missed gym with doc.robert@example.com at +1-555-867-5309"
        val result = piiRedactor.redact(original)
        
        // Assert entities are replaced
        assertTrue(!result.redactedText.contains("+1-555-867-5309"))
        assertTrue(!result.redactedText.contains("doc.robert@example.com"))
        assertTrue(result.redactedText.contains("[PHONE_1]"))
        assertTrue(result.redactedText.contains("[EMAIL_1]"))

        // Rehydration recovers the exact original text
        val rehydrated = piiRedactor.rehydrate(result.redactedText, result.tokenToRealValue)
        assertEquals(original, rehydrated)
    }
}
