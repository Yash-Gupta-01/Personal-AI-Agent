package com.aether.domain.action

import com.aether.domain.policy.DefaultRestraintPolicy
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class ProcessFrictionNudgeTest {

    private val threads = mutableMapOf<String, TrackedThread>()
    private val actions = mutableMapOf<String, ActionRecord>()

    private val threadRepo = object : TrackedThreadRepository {
        override fun observeActive(): Flow<List<TrackedThread>> = MutableStateFlow(threads.values.toList())
        override suspend fun getById(id: String): TrackedThread? = threads[id]
        override suspend fun getByTopic(topic: String): TrackedThread? = threads.values.firstOrNull { it.topic == topic }
        override suspend fun save(thread: TrackedThread) { threads[thread.id] = thread }
        override suspend fun delete(id: String) { threads.remove(id) }
    }

    private val actionRepo = object : ActionRecordRepository {
        override fun observePending(): Flow<List<ActionRecord>> = MutableStateFlow(actions.values.toList())
        override fun observeForThread(threadId: String): Flow<List<ActionRecord>> =
            MutableStateFlow(actions.values.filter { it.threadId == threadId })
        override suspend fun getById(id: String): ActionRecord? = actions[id]
        override suspend fun save(record: ActionRecord) { actions[record.id] = record }
        override suspend fun delete(id: String) { actions.remove(id) }
    }

    private val daytime = Instant.parse("2026-11-21T10:00:00Z")
    private val nighttime = Instant.parse("2026-11-21T23:30:00Z")

    @Test
    fun `missed gym note proposes 7_00 AM alarm during active hours`() = runTest {
        val useCase = ProcessFrictionNudge(threadRepo, actionRepo, DefaultRestraintPolicy())

        val action = useCase.invoke("didn't get to go to gym today", now = daytime, zone = ZoneOffset.UTC)

        assertNotNull(action)
        assertEquals("set_alarm", action!!.toolName)
        assertEquals("7", action.toolParams["hour"])
        assertEquals("0", action.toolParams["minute"])

        // Thread created and miss recorded
        val thread = threadRepo.getByTopic("gym")
        assertNotNull(thread)
        assertEquals(1, thread!!.consecutiveMisses)
    }

    @Test
    fun `non-friction note does not trigger nudge`() = runTest {
        val useCase = ProcessFrictionNudge(threadRepo, actionRepo, DefaultRestraintPolicy())

        val action = useCase.invoke("Had a great dinner with Alex", now = daytime, zone = ZoneOffset.UTC)

        assertNull(action)
        assertEquals(0, actions.size)
    }

    @Test
    fun `friction note during quiet hours is restrained`() = runTest {
        val useCase = ProcessFrictionNudge(threadRepo, actionRepo, DefaultRestraintPolicy())

        val action = useCase.invoke("missed gym again", now = nighttime, zone = ZoneOffset.UTC)

        assertNull(action) // Blocked by quiet hours
        // But miss is still recorded in thread state
        val thread = threadRepo.getByTopic("gym")
        assertNotNull(thread)
        assertEquals(1, thread!!.consecutiveMisses)
    }
}
