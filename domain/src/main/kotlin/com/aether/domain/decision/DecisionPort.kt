package com.aether.domain.decision

sealed interface DecisionQuestion {
    val instructions: String

    /** Yes/No evaluation returning a calibrated probability. */
    data class Noul(override val instructions: String) : DecisionQuestion

    /** Single-select choice from a closed map of key -> description. */
    data class Choice(
        override val instructions: String,
        val criteria: Map<String, String>,
    ) : DecisionQuestion

    /** Ordered rubric score across levels 0 to criteria.size - 1. */
    data class Score(
        override val instructions: String,
        val criteria: List<String>,
    ) : DecisionQuestion
}

sealed interface DecisionAnswer {
    val probability: Float
    val confidence: Float

    data class NoulAnswer(
        val isTrue: Boolean,
        override val probability: Float,
        override val confidence: Float,
    ) : DecisionAnswer

    data class ChoiceAnswer(
        val selectedKey: String,
        val probabilities: Map<String, Float>,
        override val probability: Float,
        override val confidence: Float,
    ) : DecisionAnswer

    data class ScoreAnswer(
        val scoreIndex: Int,
        val scoreValue: Float,
        val probabilities: Map<String, Float>,
        override val probability: Float,
        override val confidence: Float,
    ) : DecisionAnswer
}

data class DecisionResult(
    val answers: Map<String, DecisionAnswer>,
    val latencyMs: Long,
)

/**
 * Port for System 1 operational decision models.
 *
 * Evaluates typed questions over an input in a single forward pass
 * with zero generated output tokens.
 */
interface DecisionPort {
    suspend fun evaluate(
        input: String,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult
}
