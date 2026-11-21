package com.aether.domain.memory

import kotlinx.coroutines.flow.Flow

/**
 * Port for memory storage.
 *
 * Declared in the domain and implemented by infrastructure, so the rest of the
 * app never learns that Room exists. Swapping the storage engine — or faking
 * it in a test — means providing another implementation of this interface and
 * nothing else.
 *
 * Reads are a [Flow] because the dashboard must reflect a capture the instant
 * it happens, without anyone having to remember to refresh.
 */
interface MemoryRepository {

    /** Active memories, newest first. Emits again whenever storage changes. */
    fun observeActive(): Flow<List<Memory>>

    /**
     * Up to [limit] active memories that have not been classified yet,
     * oldest first so nothing captured early is starved by newer captures.
     *
     * Memories the model has already failed on [ClassifyMemories.MAX_ATTEMPTS]
     * times are excluded — without that, one memory no model can label sits at
     * the head of an oldest-first queue forever and blocks everything behind it.
     */
    suspend fun unclassified(limit: Int): List<Memory>

    /**
     * Records that classification was attempted on [id] and produced nothing
     * usable, so it eventually drops out of [unclassified].
     *
     * Separate from [save] because it must not touch `updatedAt` — a failed
     * attempt is not a change to what Aether knows.
     */
    suspend fun recordClassificationFailure(id: String)

    /** Inserts a new memory, or replaces an existing one with the same id. */
    suspend fun save(memory: Memory)

    /** Removes a memory permanently. */
    suspend fun delete(id: String)
}
