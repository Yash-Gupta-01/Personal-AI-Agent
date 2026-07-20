package com.aether.data.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface MemoryDao {

    /**
     * Active memories, newest first.
     *
     * Returning a Flow makes Room re-emit on every write, so a capture appears
     * on the dashboard without anything having to trigger a refresh.
     */
    @Query("SELECT * FROM memories WHERE status = :status ORDER BY created_at DESC")
    fun observeByStatus(status: String): Flow<List<MemoryEntity>>

    /**
     * The classification queue: active memories with no type yet that the
     * model has not already failed on [maxAttempts] times.
     *
     * Oldest first, so an early capture is never starved by newer ones. Not a
     * Flow — this is pulled by a background worker, not observed by the UI.
     */
    @Query(
        "SELECT * FROM memories WHERE status = :status AND type = :unclassified " +
            "AND classification_attempts < :maxAttempts " +
            "ORDER BY created_at ASC LIMIT :limit",
    )
    suspend fun unclassified(
        status: String,
        unclassified: String,
        maxAttempts: Int,
        limit: Int,
    ): List<MemoryEntity>

    /** Increments in SQL rather than read-modify-write, so it cannot race. */
    @Query(
        "UPDATE memories SET classification_attempts = classification_attempts + 1 " +
            "WHERE id = :id",
    )
    suspend fun incrementClassificationAttempts(id: String)

    /** REPLACE so that saving an edited memory updates the existing row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)
}
