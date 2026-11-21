package com.aether.domain.action

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Port for proactive action persistence and lifecycle queries.
 */
interface ActionRecordRepository {
    /** Emits pending actions (PROPOSED or APPROVED), newest first. */
    fun observePending(): Flow<List<ActionRecord>>

    /** Emits executed actions awaiting follow-up evaluation, newest first. */
    fun observePendingFollowUps(): Flow<List<ActionRecord>>

    /** Retrieves the most recently executed action awaiting follow-up evaluation. */
    suspend fun getLatestPendingFollowUp(): ActionRecord?

    /** Emits action history for a specific thread, newest first. */
    fun observeForThread(threadId: String): Flow<List<ActionRecord>>

    /** Retrieves an action record by ID. */
    suspend fun getById(id: String): ActionRecord?

    /** Saves or updates an action record. */
    suspend fun save(record: ActionRecord)

    /** Deletes an action record. */
    suspend fun delete(id: String)

    /** Counts proactive actions posted in the specified window [start, end). */
    suspend fun countProactivePostedInWindow(start: Instant, end: Instant): Int = 0

    /** Returns the timestamp of the most recent proactive intervention posted. */
    suspend fun getLastProactivePostedTimestamp(): Instant? = null
}
