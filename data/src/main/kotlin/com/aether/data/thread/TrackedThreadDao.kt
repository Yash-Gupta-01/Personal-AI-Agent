package com.aether.data.thread

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface TrackedThreadDao {

    @Query("SELECT * FROM tracked_threads WHERE status = 'ACTIVE' ORDER BY last_observed_at DESC")
    fun observeActive(): Flow<List<TrackedThreadEntity>>

    @Query("SELECT * FROM tracked_threads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TrackedThreadEntity?

    @Query("SELECT * FROM tracked_threads WHERE topic = :topic ORDER BY last_observed_at DESC LIMIT 1")
    suspend fun getByTopic(topic: String): TrackedThreadEntity?

    @Upsert
    suspend fun upsert(thread: TrackedThreadEntity)

    @Query("DELETE FROM tracked_threads WHERE id = :id")
    suspend fun delete(id: String)
}
