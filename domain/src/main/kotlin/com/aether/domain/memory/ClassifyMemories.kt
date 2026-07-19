package com.aether.domain.memory

import com.aether.domain.llm.LlmClient
import com.aether.domain.llm.LlmUnavailableException

/**
 * Classifies captured memories that have no type yet.
 *
 * The first use case where Aether acts on its own: the user captures a thought
 * and, without doing anything else, it acquires structure.
 *
 * Runs one memory at a time rather than batching several into one prompt. A
 * batched prompt is cheaper, but one malformed line corrupts the alignment
 * between answers and memories, and a mislabelled memory is worse than an
 * unlabelled one — it is wrong in a way nothing downstream can detect.
 */
class ClassifyMemories(
    private val repository: MemoryRepository,
    private val llmClient: LlmClient,
) {

    /**
     * Classifies up to [limit] unclassified memories.
     *
     * @return how many were successfully classified.
     */
    suspend fun invoke(limit: Int = DEFAULT_BATCH): Int {
        val pending = repository.unclassified(limit)
        if (pending.isEmpty()) return 0

        var classified = 0
        for (memory in pending) {
            val type = classify(memory) ?: break // model unavailable: stop, try later
            if (type != MemoryType.UNCLASSIFIED) {
                repository.save(memory.withType(type))
                classified++
            }
        }
        return classified
    }

    /**
     * Returns the classified type, [MemoryType.UNCLASSIFIED] if the model's
     * answer was unusable, or null if the model could not run at all.
     *
     * The distinction matters: an unusable answer is this memory's problem and
     * the next one may be fine, whereas an unavailable model means every
     * remaining memory would fail too, so the batch should stop rather than
     * burn battery failing repeatedly.
     */
    private suspend fun classify(memory: Memory): MemoryType? = try {
        val raw = llmClient.generate(
            prompt = MemoryClassifier.buildPrompt(memory.content),
            maxTokens = MemoryClassifier.MAX_TOKENS,
        )
        MemoryClassifier.parse(raw)
    } catch (_: LlmUnavailableException) {
        null
    }

    private companion object {
        // Small batches keep any single background run short, so classification
        // never holds the device awake for long.
        const val DEFAULT_BATCH = 10
    }
}
