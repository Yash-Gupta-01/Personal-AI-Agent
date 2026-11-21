package com.aether.data.privacy

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted reversible pseudonymization mapping.
 *
 * Ensures identical entities deterministically map to the same token,
 * allowing zero-leakage outbound reasoning calls and exact local re-hydration.
 */
@Entity(
    tableName = "pii_entity_map",
    indices = [
        Index(value = ["normalized_value", "entity_type"], unique = true),
    ],
)
data class PiiEntityMapEntity(
    @PrimaryKey
    @ColumnInfo(name = "token")
    val token: String,

    @ColumnInfo(name = "real_value")
    val realValue: String,

    @ColumnInfo(name = "normalized_value")
    val normalizedValue: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "created_at")
    val createdAtEpochMillis: Long,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAtEpochMillis: Long,
)
