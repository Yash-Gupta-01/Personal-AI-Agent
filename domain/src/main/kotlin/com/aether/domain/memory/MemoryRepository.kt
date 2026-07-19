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

    /** Inserts a new memory, or replaces an existing one with the same id. */
    suspend fun save(memory: Memory)

    /** Removes a memory permanently. */
    suspend fun delete(id: String)
}
