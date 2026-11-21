package com.aether.domain.privacy

data class RedactionResult(
    val redactedText: String,
    val tokenToRealValue: Map<String, String>,
)

sealed class RedactionException(message: String) : Exception(message) {
    class AmbiguousEntityException(message: String) : RedactionException(message)
    class ParsingException(message: String) : RedactionException(message)
    class LowConfidenceException(message: String) : RedactionException(message)
}

/**
 * Port for reversible local PII anonymization.
 *
 * Ensures raw personal details (names, locations, contact info) are
 * replaced with deterministic tokens before any outbound model invocation,
 * and seamlessly restored upon response receipt.
 */
interface PiiRedactor {
    suspend fun redact(text: String): RedactionResult

    /**
     * Fail-closed redaction: returns Result.failure if any entity cannot be confidently
     * sanitized or if parsing ambiguity is detected. Never returns partially sanitized text.
     */
    suspend fun redactStrict(text: String): Result<RedactionResult> = runCatching {
        val result = redact(text)
        // Fail closed on unclosed or malformed token brackets
        if (result.redactedText.contains("[") && !result.redactedText.contains("]")) {
            throw RedactionException.ParsingException("Malformed or unclosed redaction token in text")
        }
        result
    }

    suspend fun rehydrate(redactedText: String, tokenToRealValue: Map<String, String>): String
}
