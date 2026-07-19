package com.aether.data.memory

import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.memory.MemoryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of the domain's [MemoryRepository] port.
 *
 * The only class that knows both representations exist. Everything above this
 * line in the architecture deals in domain types.
 */
@Singleton
internal class RoomMemoryRepository @Inject constructor(
    private val dao: MemoryDao,
) : MemoryRepository {

    override fun observeActive(): Flow<List<Memory>> =
        dao.observeByStatus(MemoryStatus.ACTIVE.name)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun save(memory: Memory) = dao.upsert(memory.toEntity())

    override suspend fun delete(id: String) = dao.deleteById(id)
}
