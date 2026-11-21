package com.aether.domain.reasoning

/** Minimal context passed to System 2 causal reflection. */
data class ThreadContext(
    val threadTopic: String,
    val threadTitle: String,
    val recentObservations: List<String>,
    val knownBlockers: List<String>,
    val lastActionOutcome: String? = null,
)

/** Concrete device tool action and user-facing prompt. */
data class ProposedAction(
    val toolName: String,
    val toolParams: Map<String, String>,
    val notificationTitle: String,
    val notificationBody: String,
    val confirmButtonText: String,
)

/** Outcome of causal reasoning and root-cause analysis. */
data class ReasoningProposal(
    val detectedRootCause: String?,
    val proposedAction: ProposedAction?,
    val reasoningSummary: String,
)

/**
 * Port for System 2 causal reflection.
 *
 * Runs asynchronously in the background (or via BYOK endpoint) over
 * PII-redacted context to evaluate friction causes and formulate actionable nudges.
 */
interface ReasoningPort {
    suspend fun reflect(context: ThreadContext): ReasoningProposal
}
