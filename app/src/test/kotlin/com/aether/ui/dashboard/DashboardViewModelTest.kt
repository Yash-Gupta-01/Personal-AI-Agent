package com.aether.ui.dashboard

import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.memory.MemorySource
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
        override suspend fun save(memory: Memory) {
            memories.value = memories.value + memory
        }

        override suspend fun delete(id: String) {
            memories.value = memories.value.filterNot { it.id == id }
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts empty`() = runTest {
        val viewModel = DashboardViewModel(repository)

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `reflects memories from the repository`() = runTest {
        val now = Instant.parse("2026-07-19T09:00:00Z")
        memories.value = listOf(Memory.capture("Water the plants", MemorySource.MANUAL_TEXT, now))

        val viewModel = DashboardViewModel(repository)
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

        val viewModel = DashboardViewModel(repository)
        val state = viewModel.uiState.first { it.days.size == 2 }

        assertEquals(2, state.days.size)
    }

    @Test
    fun `updates when a memory is captured`() = runTest {
        val viewModel = DashboardViewModel(repository)
        assertTrue(viewModel.uiState.value.isEmpty)

        // A capture must reach the dashboard without an explicit refresh.
        repository.save(
            Memory.capture("New thought", MemorySource.MANUAL_VOICE, Instant.parse("2026-07-19T12:00:00Z")),
        )

        assertFalse(viewModel.uiState.first { !it.isEmpty }.isEmpty)
    }
}
