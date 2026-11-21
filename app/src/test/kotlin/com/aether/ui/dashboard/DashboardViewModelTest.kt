package com.aether.ui.dashboard

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.domain.llm.LlmClient
import com.aether.domain.memory.ClassifyMemories
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.MemoryType
import com.aether.domain.timeline.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for the dashboard, using a fake repository.
 *
 * These run on the JVM in milliseconds because the ViewModel depends on the
 * domain port rather than on Room — the payoff of declaring the port in
 * :domain instead of reaching for the database directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val memories = MutableStateFlow<List<Memory>>(emptyList())

    private val repository = object : MemoryRepository {
        override fun observeActive() = memories

        override suspend fun unclassified(limit: Int): List<Memory> =
            memories.value.filter { it.type == MemoryType.UNCLASSIFIED }.take(limit)

        override suspend fun recordClassificationFailure(id: String) = Unit

        override suspend fun save(memory: Memory) {
            memories.value = memories.value.filterNot { it.id == memory.id } + memory
        }

        override suspend fun delete(id: String) {
            memories.value = memories.value.filterNot { it.id == id }
        }
    }

    /** Answers with one type, then stops classifying anything. */
    private var llmAnswers = ArrayDeque<String>()

    private val llmClient = object : LlmClient {
        override suspend fun generate(prompt: String, maxTokens: Int): String =
            llmAnswers.removeFirstOrNull() ?: "UNCLASSIFIED"
    }

    private fun viewModel() =
        DashboardViewModel(repository, ClassifyMemories(repository, llmClient))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts empty`() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `reflects memories from the repository`() = runTest {
        val now = Instant.parse("2026-07-19T09:00:00Z")
        memories.value = listOf(Memory.capture("Water the plants", MemorySource.MANUAL_TEXT, now))

        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.isEmpty }

        assertFalse(state.isEmpty)
        assertEquals("Water the plants", state.days.single().memories.single().content)
    }

    @Test
    fun `groups memories across separate days`() = runTest {
        memories.value = listOf(
            Memory.capture("Monday", MemorySource.MANUAL_TEXT, Instant.parse("2026-07-13T09:00:00Z")),
            Memory.capture("Friday", MemorySource.MANUAL_TEXT, Instant.parse("2026-07-17T09:00:00Z")),
        )

        val viewModel = viewModel()
        val state = viewModel.uiState.first { it.days.size == 2 }

        assertEquals(2, state.days.size)
    }

    @Test
    fun `capture stores the typed content`() = runTest {
        val viewModel = viewModel()

        viewModel.capture("Renew the passport")

        val stored = viewModel.uiState.first { !it.isEmpty }.days.single().memories.single()
        assertEquals("Renew the passport", stored.content)
        assertEquals(MemorySource.MANUAL_TEXT, stored.source)
    }

    @Test
    fun `blank captures are ignored`() = runTest {
        val viewModel = viewModel()

        viewModel.capture("   ")

        // A mis-tap should quietly do nothing rather than store an empty card
        // or raise an error the user cannot act on.
        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `delete removes the memory`() = runTest {
        val viewModel = viewModel()
        viewModel.capture("Mistake")
        val stored = viewModel.uiState.first { !it.isEmpty }.days.single().memories.single()

        viewModel.delete(stored)

        assertTrue(viewModel.uiState.first { it.isEmpty }.isEmpty)
    }

    @Test
    fun `restore brings back the memory unchanged`() = runTest {
        val viewModel = viewModel()
        viewModel.capture("Worth keeping after all")
        val original = viewModel.uiState.first { !it.isEmpty }.days.single().memories.single()
        viewModel.delete(original)
        viewModel.uiState.first { it.isEmpty }

        viewModel.restore(original)

        // Undo must restore the original id and timestamps, so the memory
        // returns to its place in the timeline rather than jumping to the top
        // as though it were newly captured.
        val restored = viewModel.uiState.first { !it.isEmpty }.days.single().memories.single()
        assertEquals(original.id, restored.id)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.content, restored.content)
    }

    @Test
    fun `classifies a captured memory without being asked`() = runTest {
        llmAnswers = ArrayDeque(listOf("HEALTH"))
        val viewModel = viewModel()

        viewModel.capture("Stop skipping breakfast")

        // The whole point of the milestone: the user does nothing, and the
        // memory acquires a type on its own.
        val classified = viewModel.uiState
            .first { it.days.singleOrNull()?.memories?.singleOrNull()?.type == MemoryType.HEALTH }
        assertEquals(
            MemoryType.HEALTH,
            classified.days.single().memories.single().type,
        )
    }

    @Test
    fun `classifies the backlog left over from a previous session`() = runTest {
        llmAnswers = ArrayDeque(listOf("GOAL"))
        memories.value = listOf(
            Memory.capture("Run a half marathon", MemorySource.MANUAL_TEXT, Instant.parse("2026-07-18T09:00:00Z")),
        )

        val viewModel = viewModel()

        val state = viewModel.uiState
            .first { it.days.singleOrNull()?.memories?.singleOrNull()?.type == MemoryType.GOAL }
        assertEquals(MemoryType.GOAL, state.days.single().memories.single().type)
    }

    @Test
    fun `updates when a memory is captured`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isEmpty)

        // A capture must reach the dashboard without an explicit refresh.
        repository.save(
            Memory.capture("New thought", MemorySource.MANUAL_VOICE, Instant.parse("2026-07-19T12:00:00Z")),
        )

        assertFalse(viewModel.uiState.first { !it.isEmpty }.isEmpty)
    }

    @Test
    fun `timeline reflects both memories and active action proposals`() = runTest {
        val now = Instant.parse("2026-11-22T08:00:00Z")
        val actionFlow = MutableStateFlow<List<ActionRecord>>(emptyList())
        val actionRepo = object : ActionRecordRepository {
            override fun observePending() = actionFlow
            override fun observeAll() = actionFlow
            override fun observePendingFollowUps() = actionFlow
            override fun observeForThread(threadId: String) = actionFlow
            override suspend fun getLatestPendingFollowUp(): ActionRecord? = null
            override suspend fun getById(id: String): ActionRecord? = null
            override suspend fun save(record: ActionRecord) {
                actionFlow.value = actionFlow.value.filterNot { it.id == record.id } + record
            }
            override suspend fun delete(id: String) {
                actionFlow.value = actionFlow.value.filterNot { it.id == id }
            }
        }

        val viewModel = DashboardViewModel(
            repository = repository,
            classifyMemories = ClassifyMemories(repository, llmClient),
            actionRecordRepository = actionRepo,
        )

        memories.value = listOf(Memory.capture("missed gym again", MemorySource.MANUAL_TEXT, now))
        actionFlow.value = listOf(
            ActionRecord.propose(
                threadId = "gym",
                toolName = "SetAlarmTool",
                toolParams = mapOf("nudge_title" to "Morning Gym Routine"),
                now = now.plusSeconds(1),
            )
        )

        val state = viewModel.uiState.first { it.timelineDays.isNotEmpty() && it.timelineDays.first().items.size == 2 }
        val day = state.timelineDays.single()

        assertEquals(2, day.items.size)
        assertTrue(day.items[0] is TimelineItem.Intervention)
        assertTrue(day.items[1] is TimelineItem.Thought)
    }

    @Test
    fun `timeline reflects executed receipts and follow-up check-ins`() = runTest {
        val now = Instant.parse("2026-11-22T08:00:00Z")
        val actionFlow = MutableStateFlow<List<ActionRecord>>(emptyList())
        val actionRepo = object : ActionRecordRepository {
            override fun observePending() = actionFlow
            override fun observeAll() = actionFlow
            override fun observePendingFollowUps() = actionFlow
            override fun observeForThread(threadId: String) = actionFlow
            override suspend fun getLatestPendingFollowUp(): ActionRecord? = null
            override suspend fun getById(id: String): ActionRecord? = null
            override suspend fun save(record: ActionRecord) {
                actionFlow.value = actionFlow.value.filterNot { it.id == record.id } + record
            }
            override suspend fun delete(id: String) {
                actionFlow.value = actionFlow.value.filterNot { it.id == id }
            }
        }

        val viewModel = DashboardViewModel(
            repository = repository,
            classifyMemories = ClassifyMemories(repository, llmClient),
            actionRecordRepository = actionRepo,
        )

        val executedAction = ActionRecord(
            id = "act-1",
            threadId = "gym",
            toolName = "SetAlarmTool",
            toolParams = mapOf("hour" to "7", "minute" to "0"),
            status = ActionStatus.OBSERVED,
            proposedAt = now.minusSeconds(3600),
            executedAt = now.minusSeconds(1800),
            observedAt = now,
            followUpDueAt = now,
            outcomeNote = null,
            createdAt = now.minusSeconds(3600),
            updatedAt = now,
        )
        actionFlow.value = listOf(executedAction)

        val state = viewModel.uiState.first { it.timelineDays.isNotEmpty() }
        val item = state.timelineDays.single().items.single()

        assertTrue(item is TimelineItem.FollowUpCheckIn)
        assertEquals("act-1", (item as TimelineItem.FollowUpCheckIn).action.id)
    }
}
