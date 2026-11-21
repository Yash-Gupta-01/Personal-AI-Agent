package com.aether.domain.action

import kotlinx.coroutines.flow.Flow

/**
 * Port for proactive action persistence and lifecycle queries.
 */
interface ActionRecordRepository {
    /** Emits pending actions (PROPOSED or APPROVED), newest first. */
    fun observePending(): Flow<List<ActionRecord>>

    /** Emits action history for a specific thread, newest first. */
    fun observeForThread(threadId: String): Flow<List<ActionRecord>>

    /** Retrieves an action record by ID. */
    suspend fun getById(id: String): ActionRecord?

    /** Saves or updates an action record. */
    suspend fun save(record: ActionRecord)

    /** Deletes an action record. */
    suspend fun delete(id: String)
}
