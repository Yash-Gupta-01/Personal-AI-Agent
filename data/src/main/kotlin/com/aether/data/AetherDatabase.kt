package com.aether.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aether.data.memory.MemoryDao
import com.aether.data.memory.MemoryEntity

/**
 * The single local database. Aether is offline-first: this file is the whole
 * source of truth, not a cache of something remote.
 *
 * Schema JSON is exported to `data/schemas` so migrations can be reviewed as
 * diffs. There are no migrations yet — version 1 is the first schema.
 */
@Database(
    entities = [MemoryEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AetherDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val NAME = "aether.db"
    }
}
