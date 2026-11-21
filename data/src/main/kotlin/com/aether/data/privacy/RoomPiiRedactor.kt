package com.aether.data.privacy

import com.aether.domain.privacy.PiiRedactor
import com.aether.domain.privacy.RedactionResult
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RoomPiiRedactor @Inject constructor(
    private val dao: PiiEntityMapDao,
) : PiiRedactor {

    override suspend fun redact(text: String): RedactionResult {
        if (text.isBlank()) {
            return RedactionResult(redactedText = text, tokenToRealValue = emptyMap())
        }

        val tokenToReal = mutableMapOf<String, String>()
        var workingText = text
        val now = Instant.now().toEpochMilli()

        // 1. Gather all previously known PII entities from Room
        val existingEntities = dao.getAll()
            .sortedByDescending { it.realValue.length } // Longest match first

        for (entity in existingEntities) {
            if (workingText.contains(entity.realValue, ignoreCase = true)) {
                workingText = replaceWord(workingText, entity.realValue, entity.token)
                tokenToReal[entity.token] = entity.realValue
                dao.updateLastUsed(entity.token, now)
            }
        }

        // 2. Detect common dynamic patterns (emails, phone numbers)
        val emailMatches = EMAIL_REGEX.findAll(workingText).map { it.value }.distinct().toList()
        for (email in emailMatches.sortedByDescending { it.length }) {
            val token = getOrCreateToken(email, "EMAIL", now)
            workingText = workingText.replace(email, token)
            tokenToReal[token] = email
        }

        val phoneMatches = PHONE_REGEX.findAll(workingText).map { it.value }.distinct().toList()
        for (phone in phoneMatches.sortedByDescending { it.length }) {
            val token = getOrCreateToken(phone, "PHONE", now)
            workingText = workingText.replace(phone, token)
            tokenToReal[token] = phone
        }

        return RedactionResult(
            redactedText = workingText,
            tokenToRealValue = tokenToReal,
        )
    }

    override suspend fun rehydrate(
        redactedText: String,
        tokenToRealValue: Map<String, String>,
    ): String {
        var restored = redactedText
        // Replace longest tokens first
        for ((token, real) in tokenToRealValue.entries.sortedByDescending { it.key.length }) {
            restored = restored.replace(token, real)
        }
        return restored
    }

    private suspend fun getOrCreateToken(realValue: String, type: String, now: Long): String {
        val normalized = realValue.trim().lowercase()
        val existing = dao.getByNormalizedValue(normalized, type)
        if (existing != null) {
            dao.updateLastUsed(existing.token, now)
            return existing.token
        }

        val count = dao.countByType(type)
        val token = "[$type" + "_${count + 1}]"
        dao.upsert(
            PiiEntityMapEntity(
                token = token,
                realValue = realValue.trim(),
                normalizedValue = normalized,
                entityType = type,
                createdAtEpochMillis = now,
                lastUsedAtEpochMillis = now,
            ),
        )
        return token
    }

    private fun replaceWord(text: String, target: String, replacement: String): String {
        val regex = Regex("(?i)\\b" + Regex.escape(target) + "\\b")
        return regex.replace(text, replacement)
    }

    private companion object {
        val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}")
    }
}
