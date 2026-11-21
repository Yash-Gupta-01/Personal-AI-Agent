package com.aether.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.aether.data.action.ActionRecordDao
import com.aether.data.action.ActionRecordEntity
import com.aether.data.memory.MemoryDao
import com.aether.data.memory.MemoryEntity
import com.aether.data.privacy.PiiEntityMapDao
import com.aether.data.privacy.PiiEntityMapEntity
import com.aether.data.thread.TrackedThreadDao
import com.aether.data.thread.TrackedThreadEntity

/**
 * The single local database. Aether is offline-first: this file is the whole
 * source of truth, not a cache of something remote.
 *
 * Schema JSON is exported to `data/schemas` so migrations can be reviewed as
 * diffs.
 *
 * v2 added `classification_attempts`.
 * v3 adds `local_only` to `memories`, plus `tracked_threads`, `action_records`,
 * and `pii_entity_map` to support the closed-loop ambient assistant.
 */
@Database(
    entities = [
        MemoryEntity::class,
        TrackedThreadEntity::class,
        ActionRecordEntity::class,
        PiiEntityMapEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
internal abstract class AetherDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun trackedThreadDao(): TrackedThreadDao
    abstract fun actionRecordDao(): ActionRecordDao
    abstract fun piiEntityMapDao(): PiiEntityMapDao

    companion object {
        const val NAME = "aether.db"
    }
}
