package com.aether.data.action

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ActionRecordDao {

    @Query("SELECT * FROM action_records WHERE status IN ('PROPOSED', 'APPROVED') ORDER BY proposed_at DESC")
    fun observePending(): Flow<List<ActionRecordEntity>>

    @Query("SELECT * FROM action_records ORDER BY proposed_at DESC")
    fun observeAll(): Flow<List<ActionRecordEntity>>

    @Query("SELECT * FROM action_records WHERE status IN ('EXECUTED', 'OBSERVED') ORDER BY executed_at DESC")
    fun observePendingFollowUps(): Flow<List<ActionRecordEntity>>

    @Query("SELECT * FROM action_records WHERE thread_id = :threadId ORDER BY proposed_at DESC")
    fun observeForThread(threadId: String): Flow<List<ActionRecordEntity>>

    @Query("SELECT * FROM action_records WHERE status IN ('EXECUTED', 'OBSERVED') ORDER BY executed_at DESC LIMIT 1")
    suspend fun getLatestPendingFollowUp(): ActionRecordEntity?

    @Query("SELECT * FROM action_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ActionRecordEntity?

    @Upsert
    suspend fun upsert(record: ActionRecordEntity)

    @Query("SELECT COUNT(*) FROM action_records WHERE proposed_at >= :startEpochMillis AND proposed_at < :endEpochMillis AND status NOT IN ('SUPPRESSED', 'CANCELLED')")
    suspend fun countPostedInWindow(startEpochMillis: Long, endEpochMillis: Long): Int

    @Query("SELECT proposed_at FROM action_records WHERE status NOT IN ('SUPPRESSED', 'CANCELLED') ORDER BY proposed_at DESC LIMIT 1")
    suspend fun getLastPostedTimestamp(): Long?

    @Query("DELETE FROM action_records WHERE id = :id")
    suspend fun delete(id: String)
}
