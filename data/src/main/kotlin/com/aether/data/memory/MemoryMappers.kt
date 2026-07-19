package com.aether.data.memory

import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.MemoryStatus
import com.aether.domain.memory.MemoryType
import java.time.Instant

/**
 * Translation between the storage and domain representations of a memory.
 *
 * Unknown enum values fall back rather than throwing. A row written by a newer
 * version of the app — or corrupted — should degrade to something displayable,
 * not crash the dashboard and make every other memory unreachable.
 */

internal fun MemoryEntity.toDomain(): Memory = Memory(
    id = id,
    content = content,
    type = enumValueOrDefault(type, MemoryType.UNCLASSIFIED),
    source = enumValueOrDefault(source, MemorySource.MANUAL_TEXT),
    status = enumValueOrDefault(status, MemoryStatus.ACTIVE),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Memory.toEntity(): MemoryEntity = MemoryEntity(
    id = id,
    content = content,
    type = type.name,
    source = source.name,
    status = status.name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
