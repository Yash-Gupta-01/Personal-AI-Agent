package com.aether.data.thread

import com.aether.domain.thread.ThreadStatus
import com.aether.domain.thread.TrackedThread
import java.time.Instant

internal fun TrackedThreadEntity.toDomain(): TrackedThread = TrackedThread(
    id = id,
    topic = topic,
    title = title,
    status = enumValues<ThreadStatus>().firstOrNull { it.name == status } ?: ThreadStatus.ACTIVE,
    consecutiveMisses = consecutiveMisses,
    consecutiveSuccesses = consecutiveSuccesses,
    knownBlockers = if (knownBlockers.isBlank()) {
        emptyList()
    } else {
        knownBlockers.split(",").map { it.trim() }.filter { it.isNotBlank() }
    },
    lastObservedAt = Instant.ofEpochMilli(lastObservedAtEpochMillis),
    lastIntervenedAt = lastIntervenedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    cooldownUntil = cooldownUntilEpochMillis?.let { Instant.ofEpochMilli(it) },
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun TrackedThread.toEntity(): TrackedThreadEntity = TrackedThreadEntity(
    id = id,
    topic = topic,
    title = title,
    status = status.name,
    consecutiveMisses = consecutiveMisses,
    consecutiveSuccesses = consecutiveSuccesses,
    knownBlockers = knownBlockers.joinToString(","),
    lastObservedAtEpochMillis = lastObservedAt.toEpochMilli(),
    lastIntervenedAtEpochMillis = lastIntervenedAt?.toEpochMilli(),
    cooldownUntilEpochMillis = cooldownUntil?.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)
