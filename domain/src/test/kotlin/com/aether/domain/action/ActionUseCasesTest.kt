package com.aether.domain.action

import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ActionUseCasesTest {

    private val fixedNow = Instant.parse("2026-11-21T10:00:00Z")

    private val actionMap = mutableMapOf<String, ActionRecord>()
    private val threadMap = mutableMapOf<String, TrackedThread>()

    private val actionRepo = object : ActionRecordRepository {
        override fun observePending(): Flow<List<ActionRecord>> =
            flowOf(actionMap.values.filter { it.status == ActionStatus.PROPOSED || it.status == ActionStatus.APPROVED })

        override fun observePendingFollowUps(): Flow<List<ActionRecord>> =
            flowOf(actionMap.values.filter { it.status == ActionStatus.EXECUTED || it.status == ActionStatus.OBSERVED })

        override fun observeForThread(threadId: String): Flow<List<ActionRecord>> =
            flowOf(actionMap.values.filter { it.threadId == threadId })

        override suspend fun getById(id: String): ActionRecord? = actionMap[id]
        override suspend fun save(record: ActionRecord) { actionMap[record.id] = record }
        override suspend fun delete(id: String) { actionMap.remove(id) }
    }

    private val threadRepo = object : TrackedThreadRepository {
        override fun observeActive(): Flow<List<TrackedThread>> = flowOf(threadMap.values.toList())
        override suspend fun getById(id: String): TrackedThread? = threadMap[id]
        override suspend fun getByTopic(topic: String): TrackedThread? = threadMap.values.firstOrNull { it.topic == topic }
        override suspend fun save(thread: TrackedThread) { threadMap[thread.id] = thread }
        override suspend fun delete(id: String) { threadMap.remove(id) }
    }

    private lateinit var executeUseCase: ExecuteActionUseCase
    private lateinit var dismissUseCase: DismissActionUseCase
    private lateinit var evaluateUseCase: EvaluateFollowUpUseCase

    @Before
    fun setUp() {
        actionMap.clear()
        threadMap.clear()
        executeUseCase = ExecuteActionUseCase(actionRepo)
        dismissUseCase = DismissActionUseCase(actionRepo, threadRepo)
        evaluateUseCase = EvaluateFollowUpUseCase(actionRepo, threadRepo)
    }

    @Test
    fun `execute action transitions to EXECUTED and sets followUpDueAt`() = runTest {
        val action = ActionRecord.propose("thread-1", "set_alarm", mapOf("hour" to "7"), fixedNow, "act-1")
        actionMap["act-1"] = action

        val executed = executeUseCase.invoke("act-1", fixedNow)
        assertNotNull(executed)
        assertEquals(ActionStatus.EXECUTED, executed?.status)
        assertEquals(fixedNow.plusSeconds(5400), executed?.followUpDueAt)
        assertEquals(ActionStatus.EXECUTED, actionMap["act-1"]?.status)
    }

    @Test
    fun `dismiss action transitions to DISMISSED and applies thread cooldown`() = runTest {
        val thread = TrackedThread.create("gym", "Gym Routine", fixedNow, id = "thread-1")
        threadMap["thread-1"] = thread
        val action = ActionRecord.propose("thread-1", "set_alarm", mapOf("hour" to "7"), fixedNow, "act-1")
        actionMap["act-1"] = action

        val dismissed = dismissUseCase.invoke("act-1", fixedNow)
        assertNotNull(dismissed)
        assertEquals(ActionStatus.DISMISSED, dismissed?.status)
        assertNotNull(threadMap["thread-1"]?.cooldownUntil)
    }

    @Test
    fun `evaluate follow up with sleep excuse diagnoses sleep_debt blocker`() = runTest {
        val thread = TrackedThread.create("gym", "Gym Routine", fixedNow, id = "thread-1")
        threadMap["thread-1"] = thread
        val action = ActionRecord.propose("thread-1", "set_alarm", mapOf("hour" to "7"), fixedNow, "act-1")
            .transitionTo(ActionStatus.EXECUTED, fixedNow)
        actionMap["act-1"] = action

        val evaluated = evaluateUseCase.invoke("act-1", "Closed it due to lack of sleep", fixedNow)
        assertNotNull(evaluated)
        assertEquals(ActionStatus.EVALUATED, evaluated?.status)
        assertTrue(threadMap["thread-1"]?.knownBlockers?.contains("sleep_debt") == true)
    }

    @Test
    fun `evaluate follow up with success records success streak`() = runTest {
        val thread = TrackedThread.create("gym", "Gym Routine", fixedNow, id = "thread-1")
        threadMap["thread-1"] = thread
        val action = ActionRecord.propose("thread-1", "set_alarm", mapOf("hour" to "7"), fixedNow, "act-1")
            .transitionTo(ActionStatus.EXECUTED, fixedNow)
        actionMap["act-1"] = action

        val evaluated = evaluateUseCase.invoke("act-1", "Yes, went and crushed it!", fixedNow)
        assertNotNull(evaluated)
        assertEquals(ActionStatus.EVALUATED, evaluated?.status)
        assertEquals(1, threadMap["thread-1"]?.consecutiveSuccesses)
    }
}
