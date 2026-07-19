package com.aether.data.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room's representation of a memory.
 *
 * Kept separate from the domain [com.aether.domain.memory.Memory] on purpose:
 * the storage schema is free to change for storage reasons (indices, column
 * renames, denormalisation) without those concerns reaching business logic.
 *
 * Enums and timestamps are stored as primitives rather than via TypeConverters
 * so the mapping stays explicit and greppable, and so a column's meaning is
 * obvious when reading the exported schema JSON.
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "status")
    val status: String,

    /** Epoch milliseconds. Indexed because the dashboard always sorts by it. */
    @ColumnInfo(name = "created_at", index = true)
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,
)
