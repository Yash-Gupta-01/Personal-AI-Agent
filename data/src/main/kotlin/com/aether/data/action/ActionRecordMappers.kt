package com.aether.data.action

import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionStatus
import org.json.JSONObject
import java.time.Instant

internal fun ActionRecordEntity.toDomain(): ActionRecord = ActionRecord(
    id = id,
    threadId = threadId,
    toolName = toolName,
    toolParams = parseParamsJson(toolParamsJson),
    status = enumValues<ActionStatus>().firstOrNull { it.name == status } ?: ActionStatus.PROPOSED,
    proposedAt = Instant.ofEpochMilli(proposedAtEpochMillis),
    executedAt = executedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    observedAt = observedAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    followUpDueAt = followUpDueAtEpochMillis?.let { Instant.ofEpochMilli(it) },
    outcomeNote = outcomeNote,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun ActionRecord.toEntity(): ActionRecordEntity = ActionRecordEntity(
    id = id,
    threadId = threadId,
    toolName = toolName,
    toolParamsJson = encodeParamsJson(toolParams),
    status = status.name,
    proposedAtEpochMillis = proposedAt.toEpochMilli(),
    executedAtEpochMillis = executedAt?.toEpochMilli(),
    observedAtEpochMillis = observedAt?.toEpochMilli(),
    followUpDueAtEpochMillis = followUpDueAt?.toEpochMilli(),
    outcomeNote = outcomeNote,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

private fun parseParamsJson(json: String): Map<String, String> {
    if (json.isBlank() || json == "{}") return emptyMap()
    return try {
        val obj = JSONObject(json)
        val map = mutableMapOf<String, String>()
        for (key in obj.keys()) {
            map[key] = obj.getString(key)
        }
        map
    } catch (_: Exception) {
        emptyMap()
    }
}

private fun encodeParamsJson(params: Map<String, String>): String {
    val obj = JSONObject()
    for ((k, v) in params) {
        obj.put(k, v)
    }
    return obj.toString()
}
