package com.aether.domain.privacy

data class RedactionResult(
    val redactedText: String,
    val tokenToRealValue: Map<String, String>,
)

/**
 * Port for reversible local PII anonymization.
 *
 * Ensures raw personal details (names, locations, contact info) are
 * replaced with deterministic tokens before any outbound model invocation,
 * and seamlessly restored upon response receipt.
 */
interface PiiRedactor {
    suspend fun redact(text: String): RedactionResult
    suspend fun rehydrate(redactedText: String, tokenToRealValue: Map<String, String>): String
}
