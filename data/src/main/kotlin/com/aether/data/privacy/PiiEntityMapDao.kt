package com.aether.data.privacy

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface PiiEntityMapDao {

    @Query("SELECT * FROM pii_entity_map WHERE token = :token LIMIT 1")
    suspend fun getByToken(token: String): PiiEntityMapEntity?

    @Query("SELECT * FROM pii_entity_map WHERE normalized_value = :normalizedValue AND entity_type = :entityType LIMIT 1")
    suspend fun getByNormalizedValue(normalizedValue: String, entityType: String): PiiEntityMapEntity?

    @Query("SELECT COUNT(*) FROM pii_entity_map WHERE entity_type = :entityType")
    suspend fun countByType(entityType: String): Int

    @Query("SELECT * FROM pii_entity_map")
    suspend fun getAll(): List<PiiEntityMapEntity>

    @Upsert
    suspend fun upsert(entity: PiiEntityMapEntity)

    @Query("UPDATE pii_entity_map SET last_used_at = :lastUsedAt WHERE token = :token")
    suspend fun updateLastUsed(token: String, lastUsedAt: Long)
}
