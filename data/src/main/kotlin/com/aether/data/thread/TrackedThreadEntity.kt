package com.aether.data.thread

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Storage representation of a tracked routine or ongoing thread.
 */
@Entity(
    tableName = "tracked_threads",
    indices = [
        Index("topic"),
        Index("status"),
    ],
)
data class TrackedThreadEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "topic")
    val topic: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "consecutive_misses", defaultValue = "0")
    val consecutiveMisses: Int = 0,

    @ColumnInfo(name = "consecutive_successes", defaultValue = "0")
    val consecutiveSuccesses: Int = 0,

    @ColumnInfo(name = "known_blockers", defaultValue = "")
    val knownBlockers: String = "",

    @ColumnInfo(name = "last_observed_at")
    val lastObservedAtEpochMillis: Long,

    @ColumnInfo(name = "last_intervened_at")
    val lastIntervenedAtEpochMillis: Long? = null,

    @ColumnInfo(name = "cooldown_until")
    val cooldownUntilEpochMillis: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,
)
