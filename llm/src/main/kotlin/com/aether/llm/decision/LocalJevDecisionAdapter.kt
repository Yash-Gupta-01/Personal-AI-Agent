package com.aether.llm.decision

import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.domain.decision.LocalDecisionPort
import com.aether.domain.privacy.LocalDecisionInput
import com.aether.llm.NativeLlama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * On-device System 1 Decision Model adapter using llama.cpp JNI.
 *
 * Implements single-forward-pass logit scoring (zero token decoding)
 * over the local GGUF model (e.g. feder-cr/jev or jevos-v4).
 */
@Singleton
class LocalJevDecisionAdapter @Inject constructor(
    private val modelStore: com.aether.llm.ModelStore? = null,
) : LocalDecisionPort, DecisionPort {

    override suspend fun evaluate(
        input: LocalDecisionInput,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult = evaluate(input.rawText, questions)

    @Volatile
    private var modelHandle: Long = 0L

    val isModelLoaded: Boolean
        get() {
            if (modelHandle != 0L) return true
            if (modelStore?.isModelPresent() == true) {
                loadModel(modelStore.modelFile.absolutePath)
            }
            return modelHandle != 0L
        }

    /**
     * Loads a decision model GGUF from [modelPath].
     * Context size is small (e.g. 1024 or 2048) since System 1 tasks are concise.
     */
    fun loadModel(modelPath: String, contextSize: Int = 2048, threads: Int = 4): Boolean {
        if (!NativeLlama.isAvailable) return false
        val file = File(modelPath)
        if (!file.exists()) return false

        synchronized(this) {
            if (modelHandle != 0L) {
                NativeLlama.freeModel(modelHandle)
                modelHandle = 0L
            }
            modelHandle = NativeLlama.loadModel(modelPath, contextSize, threads)
            return modelHandle != 0L
        }
    }

    fun unloadModel() {
        synchronized(this) {
            if (modelHandle != 0L) {
                NativeLlama.freeModel(modelHandle)
                modelHandle = 0L
            }
        }
    }

    override suspend fun evaluate(
        input: String,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult = withContext(Dispatchers.Default) {
        val handle = modelHandle
        if (handle == 0L) {
            throw IllegalStateException("Local decision model is not loaded")
        }

        val startTime = System.currentTimeMillis()
        val answers = mutableMapOf<String, DecisionAnswer>()

        synchronized(this@LocalJevDecisionAdapter) {
            for ((key, question) in questions) {
                when (question) {
                    is DecisionQuestion.Noul -> {
                        val prompt = buildNoulPrompt(input, question.instructions)
                        val candidates = arrayOf(" Yes", " No")
                        val scores = NativeLlama.scoreCandidates(handle, prompt, candidates)
                            ?: floatArrayOf(0.5f, 0.5f)
                        val yesProb = min(0.99f, max(0.01f, scores[0]))
                        val isTrue = yesProb >= 0.5f
                        val confidence = abs(yesProb - 0.5f) * 2f
                        answers[key] = DecisionAnswer.NoulAnswer(
                            isTrue = isTrue,
                            probability = yesProb,
                            confidence = confidence,
                        )
                    }
                    is DecisionQuestion.Choice -> {
                        val keysList = question.criteria.keys.toList()
                        val prompt = buildChoicePrompt(input, question.instructions, question.criteria, keysList)
                        val candidates = keysList.map { " $it" }.toTypedArray()
                        val scores = NativeLlama.scoreCandidates(handle, prompt, candidates)
                            ?: FloatArray(keysList.size) { 1f / keysList.size }
                        
                        val probsMap = mutableMapOf<String, Float>()
                        var maxProb = -1f
                        var bestKey = keysList.firstOrNull() ?: ""
                        for (i in keysList.indices) {
                            val k = keysList[i]
                            val p = scores.getOrElse(i) { 0f }
                            probsMap[k] = p
                            if (p > maxProb) {
                                maxProb = p
                                bestKey = k
                            }
                        }
                        answers[key] = DecisionAnswer.ChoiceAnswer(
                            selectedKey = bestKey,
                            probabilities = probsMap,
                            probability = maxProb,
                            confidence = maxProb,
                        )
                    }
                    is DecisionQuestion.Score -> {
                        val levels = question.criteria.indices.toList()
                        val prompt = buildScorePrompt(input, question.instructions, question.criteria)
                        val candidates = levels.map { " $it" }.toTypedArray()
                        val scores = NativeLlama.scoreCandidates(handle, prompt, candidates)
                            ?: FloatArray(levels.size) { 1f / levels.size }

                        val probsMap = mutableMapOf<String, Float>()
                        var expectedScore = 0f
                        var maxProb = -1f
                        var bestLevel = 0
                        for (i in levels.indices) {
                            val p = scores.getOrElse(i) { 0f }
                            probsMap[i.toString()] = p
                            expectedScore += i * p
                            if (p > maxProb) {
                                maxProb = p
                                bestLevel = i
                            }
                        }
                        answers[key] = DecisionAnswer.ScoreAnswer(
                            scoreIndex = bestLevel,
                            scoreValue = expectedScore,
                            probabilities = probsMap,
                            probability = maxProb,
                            confidence = maxProb,
                        )
                    }
                }
            }
        }

        val latency = System.currentTimeMillis() - startTime
        DecisionResult(answers = answers, latencyMs = latency)
    }

    private fun buildNoulPrompt(context: String, instructions: String): String = buildString {
        append("<|im_start|>system\nYou are an operational decision evaluator. Answer with Yes or No.<|im_end|>\n")
        append("<|im_start|>user\nContext: ").append(context.trim()).append("\n")
        append("Question: ").append(instructions.trim()).append("<|im_end|>\n")
        append("<|im_start|>assistant\nAnswer:")
    }

    private fun buildChoicePrompt(
        context: String,
        instructions: String,
        criteria: Map<String, String>,
        keys: List<String>,
    ): String = buildString {
        append("<|im_start|>system\nYou evaluate operational context and select the best matching category.<|im_end|>\n")
        append("<|im_start|>user\nContext: ").append(context.trim()).append("\n")
        append("Task: ").append(instructions.trim()).append("\nOptions:\n")
        for (k in keys) {
            append("- ").append(k).append(": ").append(criteria[k] ?: "").append("\n")
        }
        append("<|im_end|>\n<|im_start|>assistant\nSelected:")
    }

    private fun buildScorePrompt(
        context: String,
        instructions: String,
        criteria: List<String>,
    ): String = buildString {
        append("<|im_start|>system\nYou evaluate operational context against ordered rubric levels.<|im_end|>\n")
        append("<|im_start|>user\nContext: ").append(context.trim()).append("\n")
        append("Evaluation: ").append(instructions.trim()).append("\nRubric:\n")
        for ((idx, desc) in criteria.withIndex()) {
            append(idx).append(": ").append(desc).append("\n")
        }
        append("<|im_end|>\n<|im_start|>assistant\nScore:")
    }
}
