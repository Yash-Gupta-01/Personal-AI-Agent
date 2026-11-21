package com.aether.domain.thread

import kotlinx.coroutines.flow.Flow

/**
 * Port for tracked habit and routine persistence.
 */
interface TrackedThreadRepository {
    /** Emits active threads, newest observed first. */
    fun observeActive(): Flow<List<TrackedThread>>

    /** Retrieves a thread by ID. */
    suspend fun getById(id: String): TrackedThread?

    /** Retrieves an active or recent thread matching [topic]. */
    suspend fun getByTopic(topic: String): TrackedThread?

    /** Inserts or updates a thread. */
    suspend fun save(thread: TrackedThread)

    /** Deletes a thread permanently. */
    suspend fun delete(id: String)
}
