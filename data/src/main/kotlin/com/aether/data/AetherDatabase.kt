package com.aether.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.aether.data.memory.MemoryDao
import com.aether.data.memory.MemoryEntity

/**
 * The single local database. Aether is offline-first: this file is the whole
 * source of truth, not a cache of something remote.
 *
 * Schema JSON is exported to `data/schemas` so migrations can be reviewed as
 * diffs.
 *
 * v2 adds `classification_attempts`. Hand-written migrations are worth the
 * effort only where Room cannot infer the change; adding a column with a
 * default is precisely the case it can, so the migration is declared and Room
 * generates the SQL.
 */
@Database(
    entities = [MemoryEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
internal abstract class AetherDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val NAME = "aether.db"
    }
}
