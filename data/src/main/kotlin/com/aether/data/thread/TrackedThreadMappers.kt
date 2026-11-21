package com.aether.data.thread

import com.aether.domain.thread.ObstacleWindow
import com.aether.domain.thread.ThreadStatus
import com.aether.domain.thread.TrackedThread
import java.time.Instant

internal fun TrackedThreadEntity.toDomain(): TrackedThread {
    val rawBlockers = if (knownBlockers.isBlank()) {
        emptyList()
    } else {
        knownBlockers.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    var obstacleWindow: ObstacleWindow? = null
    val filteredBlockers = mutableListOf<String>()

    for (item in rawBlockers) {
        if (item.startsWith("OBSTACLE[") && item.endsWith("]")) {
            val content = item.removeSurrounding("OBSTACLE[", "]")
            val parts = content.split("|")
            if (parts.size == 4) {
                try {
                    obstacleWindow = ObstacleWindow(
                        obstacleType = parts[0],
                        startedAt = Instant.ofEpochMilli(parts[1].toLong()),
                        expiresAt = Instant.ofEpochMilli(parts[2].toLong()),
                        observationCount = parts[3].toInt(),
                    )
                } catch (_: Exception) {
                    // Ignore parse error
                }
            }
        } else {
            filteredBlockers.add(item)
        }
    }

    return TrackedThread(
        id = id,
        topic = topic,
        title = title,
        status = enumValues<ThreadStatus>().firstOrNull { it.name == status } ?: ThreadStatus.ACTIVE,
        consecutiveMisses = consecutiveMisses,
        consecutiveSuccesses = consecutiveSuccesses,
        knownBlockers = filteredBlockers,
        activeObstacle = obstacleWindow,
        lastObservedAt = Instant.ofEpochMilli(lastObservedAtEpochMillis),
        lastIntervenedAt = lastIntervenedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
        cooldownUntil = cooldownUntilEpochMillis?.let { Instant.ofEpochMilli(it) },
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

internal fun TrackedThread.toEntity(): TrackedThreadEntity {
    val obstacle = activeObstacle
    val serializedBlockers = if (obstacle != null) {
        val obstacleTag = "OBSTACLE[${obstacle.obstacleType}|${obstacle.startedAt.toEpochMilli()}|${obstacle.expiresAt.toEpochMilli()}|${obstacle.observationCount}]"
        (knownBlockers + obstacleTag).joinToString(",")
    } else {
        knownBlockers.joinToString(",")
    }

    return TrackedThreadEntity(
        id = id,
        topic = topic,
        title = title,
        status = status.name,
        consecutiveMisses = consecutiveMisses,
        consecutiveSuccesses = consecutiveSuccesses,
        knownBlockers = serializedBlockers,
        lastObservedAtEpochMillis = lastObservedAt.toEpochMilli(),
        lastIntervenedAtEpochMillis = lastIntervenedAt?.toEpochMilli(),
        cooldownUntilEpochMillis = cooldownUntil?.toEpochMilli(),
        createdAtEpochMillis = createdAt.toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )
}
