package com.aether.domain.memory

import com.aether.domain.llm.LlmClient
import com.aether.domain.llm.LlmUnavailableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Exercises the classification loop against a fake model.
 *
 * Runs on the JVM in milliseconds because the use case depends on the
 * [LlmClient] port rather than on llama.cpp — which is the entire reason the
 * port exists. These behaviours would otherwise need a device and a 1GB model.
 */
class ClassifyMemoriesTest {

    private val now: Instant = Instant.parse("2026-07-19T10:00:00Z")

    private class FakeRepository(initial: List<Memory> = emptyList()) : MemoryRepository {
        val stored: MutableMap<String, Memory> =
            initial.associateBy { it.id }.toMutableMap()
        val attempts: MutableMap<String, Int> = mutableMapOf()

        override fun observeActive(): Flow<List<Memory>> = flowOf(stored.values.toList())

        override suspend fun unclassified(limit: Int): List<Memory> =
            stored.values
                .filter { it.type == MemoryType.UNCLASSIFIED }
                .filter { (attempts[it.id] ?: 0) < ClassifyMemories.MAX_ATTEMPTS }
                .sortedBy { it.createdAt }
                .take(limit)

        override suspend fun recordClassificationFailure(id: String) {
            attempts[id] = (attempts[id] ?: 0) + 1
        }

        override suspend fun save(memory: Memory) {
            stored[memory.id] = memory
            attempts.remove(memory.id)
        }

        override suspend fun delete(id: String) {
            stored.remove(id)
        }
    }

    private fun memory(content: String, at: Instant = now) =
        Memory.capture(content, MemorySource.MANUAL_TEXT, at)

    private fun llmReturning(vararg outputs: String): LlmClient {
        val queue = ArrayDeque(outputs.toList())
        return object : LlmClient {
            override suspend fun generate(prompt: String, maxTokens: Int): String =
                queue.removeFirstOrNull() ?: "UNCLASSIFIED"
        }
    }

    @Test
    fun `classifies an unclassified memory`() = runTest {
        val repository = FakeRepository(listOf(memory("I want to run a half marathon")))

        val count = ClassifyMemories(repository, llmReturning("GOAL")).invoke()

        assertEquals(1, count)
        assertEquals(MemoryType.GOAL, repository.stored.values.single().type)
    }

    @Test
    fun `leaves already classified memories alone`() = runTest {
        val classified = memory("Already sorted").withType(MemoryType.HABIT, now)
        val repository = FakeRepository(listOf(classified))

        val count = ClassifyMemories(repository, llmReturning("GOAL")).invoke()

        assertEquals(0, count)
        assertEquals(MemoryType.HABIT, repository.stored.values.single().type)
    }

    @Test
    fun `unusable model output leaves the memory unclassified and retryable`() = runTest {
        val repository = FakeRepository(listOf(memory("Something vague")))

        val count = ClassifyMemories(repository, llmReturning("banana")).invoke()

        // Still in the queue, still unlabelled — better than a confident guess.
        assertEquals(0, count)
        assertEquals(MemoryType.UNCLASSIFIED, repository.stored.values.single().type)
        assertEquals(1, repository.attempts.values.single())
    }

    @Test
    fun `a memory the model keeps failing on leaves the queue`() = runTest {
        val repository = FakeRepository(listOf(memory("Untypeable")))
        val nonsense = object : LlmClient {
            var calls = 0
            override suspend fun generate(prompt: String, maxTokens: Int): String {
                calls++
                return "banana"
            }
        }

        repeat(ClassifyMemories.MAX_ATTEMPTS + 2) {
            ClassifyMemories(repository, nonsense).invoke()
        }

        // Without the attempt cap this memory sits at the head of an
        // oldest-first queue forever, blocking every capture behind it.
        assertEquals(ClassifyMemories.MAX_ATTEMPTS, nonsense.calls)
        assertTrue(repository.unclassified(10).isEmpty())
    }

    @Test
    fun `content is never altered by classification`() = runTest {
        val original = memory("Exact wording matters")
        val repository = FakeRepository(listOf(original))

        ClassifyMemories(repository, llmReturning("FACT")).invoke()

        val stored = repository.stored.values.single()
        assertEquals(original.content, stored.content)
        assertEquals(original.id, stored.id)
        assertEquals(original.createdAt, stored.createdAt)
    }

    @Test
    fun `stops the batch when the model is unavailable`() = runTest {
        val repository = FakeRepository(
            listOf(memory("One", now), memory("Two", now.plusSeconds(1))),
        )
        var calls = 0
        val unavailable = object : LlmClient {
            override suspend fun generate(prompt: String, maxTokens: Int): String {
                calls++
                throw LlmUnavailableException("no model")
            }
        }

        val count = ClassifyMemories(repository, unavailable).invoke()

        assertEquals(0, count)
        // Stops after the first failure rather than retrying every memory:
        // if no model is loaded, the rest would fail identically and only
        // burn battery.
        assertEquals(1, calls)
    }

    @Test
    fun `classifies several memories in one run`() = runTest {
        val repository = FakeRepository(
            listOf(
                memory("Switch to backend work", now),
                memory("Stop skipping breakfast", now.plusSeconds(1)),
            ),
        )

        val count = ClassifyMemories(repository, llmReturning("CAREER", "HEALTH")).invoke()

        assertEquals(2, count)
        val byContent = repository.stored.values.associateBy { it.content }
        assertEquals(MemoryType.CAREER, byContent.getValue("Switch to backend work").type)
        assertEquals(MemoryType.HEALTH, byContent.getValue("Stop skipping breakfast").type)
    }

    @Test
    fun `does nothing when there is nothing to classify`() = runTest {
        val repository = FakeRepository()

        assertEquals(0, ClassifyMemories(repository, llmReturning("GOAL")).invoke())
    }

    @Test
    fun `respects the batch limit`() = runTest {
        val repository = FakeRepository(
            (1..5).map { memory("Thought $it", now.plusSeconds(it.toLong())) },
        )

        val count = ClassifyMemories(repository, llmReturning("GOAL", "GOAL")).invoke(limit = 2)

        // Bounded work per run keeps any background pass short.
        assertEquals(2, count)
    }
}
