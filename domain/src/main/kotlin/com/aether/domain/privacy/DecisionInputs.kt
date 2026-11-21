package com.aether.domain.privacy

import com.aether.domain.memory.Memory

/**
 * Explicit exception thrown when an attempt is made to convert a [Memory.localOnly]
 * memory into a cloud-eligible payload.
 */
class PrivacyInvariantViolationException(message: String) : Exception(message)

/**
 * Unsanitized, direct input payload for on-device decision inference ([com.aether.domain.decision.LocalDecisionPort]).
 *
 * May contain raw local PII since it never crosses device boundaries.
 */
data class LocalDecisionInput(
    val rawText: String,
    val metadata: Map<String, String> = emptyMap(),
)

/**
 * Certified, provably sanitized decision input for outbound cloud decision models
 * ([com.aether.domain.decision.CloudDecisionPort]).
 *
 * Constructor is private. Instances can ONLY be created via [CloudSafeDecisionInput.create],
 * which enforces:
 * 1. Runtime rejection of [Memory.localOnly] memories.
 * 2. Fail-closed deterministic PII redaction via [PiiRedactor.redactStrict].
 */
class CloudSafeDecisionInput private constructor(
    val sanitizedText: String,
    val sourceMemoryId: String,
    val tokenToRealValue: Map<String, String>,
) {
    companion object {
        /**
         * The sole production factory for [CloudSafeDecisionInput].
         *
         * Enforces that:
         * - If [memory.localOnly] is true, returns [Result.failure] with [PrivacyInvariantViolationException].
         * - Redaction is executed via [PiiRedactor.redactStrict]. If redaction fails or is ambiguous,
         *   returns [Result.failure] without partially redacted leaks.
         */
        suspend fun create(
            memory: Memory,
            redactor: PiiRedactor,
        ): Result<CloudSafeDecisionInput> {
            if (memory.localOnly) {
                return Result.failure(
                    PrivacyInvariantViolationException(
                        "Privacy Invariant Violation: Memory '${memory.id}' is marked localOnly and cannot be routed to cloud."
                    )
                )
            }

            val redactionResult = redactor.redactStrict(memory.content)
            if (redactionResult.isFailure) {
                return Result.failure(
                    redactionResult.exceptionOrNull() ?: RedactionException.AmbiguousEntityException("Redaction failed")
                )
            }

            val result = redactionResult.getOrThrow()
            return Result.success(
                CloudSafeDecisionInput(
                    sanitizedText = result.redactedText,
                    sourceMemoryId = memory.id,
                    tokenToRealValue = result.tokenToRealValue,
                )
            )
        }

        /**
         * Testing harness for mocking cloud boundaries without exposing public constructor.
         */
        fun createForTesting(
            sanitizedText: String,
            sourceMemoryId: String = "test-mem",
            tokenToRealValue: Map<String, String> = emptyMap(),
        ): CloudSafeDecisionInput = CloudSafeDecisionInput(
            sanitizedText = sanitizedText,
            sourceMemoryId = sourceMemoryId,
            tokenToRealValue = tokenToRealValue,
        )
    }
}
