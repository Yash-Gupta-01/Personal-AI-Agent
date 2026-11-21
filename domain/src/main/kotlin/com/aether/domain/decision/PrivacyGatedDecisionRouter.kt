package com.aether.domain.decision

import com.aether.domain.memory.Memory
import com.aether.domain.privacy.CloudSafeDecisionInput
import com.aether.domain.privacy.LocalDecisionInput
import com.aether.domain.privacy.PiiRedactor

/**
 * Routes decision evaluation between on-device local execution and outbound cloud models
 * with strict privacy invariants.
 *
 * Invariants:
 * 1. [Memory.localOnly] memories are permanently excluded from [CloudDecisionPort].
 * 2. Cloud evaluation requires successful construction of [CloudSafeDecisionInput].
 * 3. Any redaction failure or ambiguity immediately fails closed with ZERO outbound network calls.
 */
class PrivacyGatedDecisionRouter(
    private val localPort: LocalDecisionPort,
    private val cloudPort: CloudDecisionPort,
    private val piiRedactor: PiiRedactor,
) {
    suspend fun evaluate(
        memory: Memory,
        questions: Map<String, DecisionQuestion>,
        preferLocal: Boolean = false,
    ): Result<DecisionResult> {
        // Invariant 1: Local-only memories are strictly processed on-device
        if (memory.localOnly || preferLocal) {
            return runCatching {
                localPort.evaluate(LocalDecisionInput(memory.content), questions)
            }
        }

        // Invariant 2: Create certified CloudSafeDecisionInput via fail-closed redaction
        val cloudSafeInputResult = CloudSafeDecisionInput.create(memory, piiRedactor)
        if (cloudSafeInputResult.isFailure) {
            // Fail closed: Do NOT invoke cloudPort
            return Result.failure(cloudSafeInputResult.exceptionOrNull()!!)
        }

        // Invariant 3: Invoke cloud port with certified input
        return runCatching {
            cloudPort.evaluate(cloudSafeInputResult.getOrThrow(), questions)
        }
    }
}
