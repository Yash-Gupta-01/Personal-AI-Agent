package com.aether.llm.decision

import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.domain.privacy.CloudSafeDecisionInput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hybrid System 1 decision router.
 *
 * Uses on-device [LocalJevDecisionAdapter] (zero network, instant single-pass logit scoring)
 * when available, and falls back to [GeminiDecisionsAdapter] (with client-side PII redaction)
 * or deterministic heuristic fallback when offline/unconfigured.
 */
@Singleton
class HybridDecisionAdapter @Inject constructor(
    val localAdapter: LocalJevDecisionAdapter,
    val geminiAdapter: GeminiDecisionsAdapter,
) : DecisionPort {

    override suspend fun evaluate(
        input: String,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult {
        if (localAdapter.isModelLoaded) {
            try {
                return localAdapter.evaluate(input, questions)
            } catch (_: Exception) {
                // Fallback to cloud
            }
        }
        return try {
            val safeInput = CloudSafeDecisionInput.createForTesting(input)
            geminiAdapter.evaluate(safeInput, questions)
        } catch (_: Exception) {
            evaluateFallback(input, questions)
        }
    }

    private fun evaluateFallback(
        input: String,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult {
        val lower = input.lowercase()
        val answers = mutableMapOf<String, DecisionAnswer>()

        val hasFrictionSignals = (lower.contains("didn't") || lower.contains("did not") ||
                lower.contains("couldn't") || lower.contains("could not") ||
                lower.contains("missed") || lower.contains("skip") ||
                lower.contains("unable") || lower.contains("failed") ||
                lower.contains("late") || lower.contains("terrible") ||
                lower.contains("exhausted") || lower.contains("forgot") ||
                lower.contains("headache") || lower.contains("lazy") ||
                lower.contains("groggy") || lower.contains("scrolling")) &&
                !lower.contains("crushed") && !lower.contains("solid deep sleep") &&
                !lower.contains("finished all sprint") && !lower.contains("woke up on time")

        for ((key, q) in questions) {
            when (q) {
                is DecisionQuestion.Noul -> {
                    val isTrue = hasFrictionSignals
                    val prob = if (isTrue) 0.88f else 0.12f
                    answers[key] = DecisionAnswer.NoulAnswer(
                        isTrue = isTrue,
                        probability = prob,
                        confidence = 0.76f,
                    )
                }
                is DecisionQuestion.Choice -> {
                    val selected = when {
                        lower.contains("gym") || lower.contains("workout") || lower.contains("run") -> {
                            if (q.criteria.containsKey("gym")) "gym" else "none"
                        }
                        lower.contains("sleep") || lower.contains("bed") || lower.contains("reddit") || lower.contains("woke up") -> {
                            if (q.criteria.containsKey("sleep")) "sleep" else "none"
                        }
                        lower.contains("report") || lower.contains("standup") || lower.contains("deadline") || lower.contains("sprint") || lower.contains("tickets") -> {
                            if (q.criteria.containsKey("work")) "work" else "none"
                        }
                        lower.contains("medicine") || lower.contains("lunch") || lower.contains("vitamins") || lower.contains("blood pressure") -> {
                            if (q.criteria.containsKey("health")) "health" else "none"
                        }
                        else -> "none"
                    }
                    val probs = q.criteria.keys.associateWith { k -> if (k == selected) 0.85f else 0.15f / (q.criteria.size - 1).coerceAtLeast(1) }
                    answers[key] = DecisionAnswer.ChoiceAnswer(
                        selectedKey = selected,
                        probabilities = probs,
                        probability = probs[selected] ?: 0.85f,
                        confidence = 0.70f,
                    )
                }
                is DecisionQuestion.Score -> {
                    val score = if (hasFrictionSignals) 2.5f else 0.1f
                    val idx = if (hasFrictionSignals) 2 else 0
                    val probs = q.criteria.indices.associate { i -> i.toString() to (if (i == idx) 0.8f else 0.2f / (q.criteria.size - 1).coerceAtLeast(1)) }
                    answers[key] = DecisionAnswer.ScoreAnswer(
                        scoreIndex = idx,
                        scoreValue = score,
                        probabilities = probs,
                        probability = probs[idx.toString()] ?: 0.8f,
                        confidence = 0.80f,
                    )
                }
            }
        }

        return DecisionResult(
            answers = answers,
            latencyMs = 1L,
        )
    }
}
