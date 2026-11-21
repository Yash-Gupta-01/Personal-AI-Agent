package com.aether.data.action

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aether.data.thread.TrackedThreadEntity

/**
 * Storage representation of an action proposed and taken by Aether.
 */
@Entity(
    tableName = "action_records",
    foreignKeys = [
        ForeignKey(
            entity = TrackedThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("thread_id"),
        Index("status"),
    ],
)
data class ActionRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "thread_id")
    val threadId: String,

    @ColumnInfo(name = "tool_name")
    val toolName: String,

    @ColumnInfo(name = "tool_params_json")
    val toolParamsJson: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "proposed_at")
    val proposedAtEpochMillis: Long,

    @ColumnInfo(name = "executed_at")
    val executedAtEpochMillis: Long? = null,

    @ColumnInfo(name = "observed_at")
    val observedAtEpochMillis: Long? = null,

    @ColumnInfo(name = "follow_up_due_at")
    val followUpDueAtEpochMillis: Long? = null,

    @ColumnInfo(name = "outcome_note")
    val outcomeNote: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMillis: Long,
)
