package com.aether.llm.decision

import com.aether.domain.decision.CloudDecisionPort
import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.domain.privacy.CloudSafeDecisionInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * Cloud adapter for Google's Gemini Decisions API (v1beta/decisions).
 *
 * Implements [CloudDecisionPort].
 * Compile-time enforcement: Only accepts certified [CloudSafeDecisionInput].
 * Raw String, Memory, or LocalDecisionInput cannot compile or be transmitted.
 */
class GeminiDecisionsAdapter(
    private val apiKeyProvider: () -> String? = { null },
) : CloudDecisionPort {

    override suspend fun evaluate(
        input: CloudSafeDecisionInput,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider() ?: throw IllegalStateException("Gemini API key is not configured")
        val startTime = System.currentTimeMillis()

        // Build JSON request payload matching Google's Gemini Decisions API schema
        val requestJson = JSONObject()
        requestJson.put("model", "gemini-decision-1")
        requestJson.put("input", input.sanitizedText)

        val questionsObj = JSONObject()
        for ((key, q) in questions) {
            val qObj = JSONObject()
            qObj.put("instructions", q.instructions)
            when (q) {
                is DecisionQuestion.Noul -> {
                    qObj.put("type", "noul")
                }
                is DecisionQuestion.Choice -> {
                    qObj.put("type", "choice")
                    val criteriaObj = JSONObject()
                    for ((cKey, cDesc) in q.criteria) {
                        criteriaObj.put(cKey, cDesc)
                    }
                    qObj.put("criteria", criteriaObj)
                }
                is DecisionQuestion.Score -> {
                    qObj.put("type", "score")
                    val criteriaArray = JSONArray()
                    for (criterion in q.criteria) {
                        criteriaArray.put(criterion)
                    }
                    qObj.put("criteria", criteriaArray)
                }
            }
            questionsObj.put(key, qObj)
        }
        requestJson.put("questions", questionsObj)

        // 3. Execute HTTP POST
        val endpointUrl = URL("$BASE_URL?key=$apiKey")
        val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
        }

        conn.outputStream.use { os ->
            os.write(requestJson.toString().toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            throw IllegalStateException("Gemini Decisions API failed ($responseCode): $err")
        }

        val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(responseBody)
        val answersJson = responseJson.optJSONObject("answers") ?: JSONObject()

        val parsedAnswers = mutableMapOf<String, DecisionAnswer>()

        for ((key, question) in questions) {
            val aObj = answersJson.optJSONObject(key) ?: continue
            when (question) {
                is DecisionQuestion.Noul -> {
                    val rawProb = if (aObj.has("noul")) {
                        aObj.optDouble("noul", 0.5).toFloat()
                    } else {
                        aObj.optJSONObject("noul")?.optDouble("probability", 0.5)?.toFloat() ?: 0.5f
                    }
                    val clampedProb = min(0.99f, max(0.01f, rawProb))
                    val isTrue = clampedProb >= 0.5f
                    val defaultConf = (if (clampedProb >= 0.5f) clampedProb - 0.5f else 0.5f - clampedProb) * 2.0
                    val conf = aObj.optDouble("confidence", defaultConf).toFloat()
                    parsedAnswers[key] = DecisionAnswer.NoulAnswer(
                        isTrue = isTrue,
                        probability = clampedProb,
                        confidence = conf,
                    )
                }
                is DecisionQuestion.Choice -> {
                    val selected = if (aObj.has("choice")) {
                        aObj.optString("choice", "")
                    } else {
                        aObj.optJSONObject("choice")?.optString("answer", "") ?: ""
                    }
                    val probsObj = aObj.optJSONObject("probabilities")
                        ?: aObj.optJSONObject("choice")?.optJSONObject("probabilities")
                    val probsMap = mutableMapOf<String, Float>()
                    if (probsObj != null) {
                        for (pKey in probsObj.keys()) {
                            probsMap[pKey] = probsObj.optDouble(pKey, 0.0).toFloat()
                        }
                    }
                    val prob = probsMap[selected] ?: 0.5f
                    val conf = aObj.optDouble("confidence", prob.toDouble()).toFloat()
                    parsedAnswers[key] = DecisionAnswer.ChoiceAnswer(
                        selectedKey = selected,
                        probabilities = probsMap,
                        probability = prob,
                        confidence = conf,
                    )
                }
                is DecisionQuestion.Score -> {
                    val scoreVal = if (aObj.has("score")) {
                        aObj.optDouble("score", 0.0).toFloat()
                    } else {
                        aObj.optJSONObject("score")?.optDouble("score", 0.0)?.toFloat() ?: 0.0f
                    }
                    val probsMap = mutableMapOf<String, Float>()
                    val probsObj = aObj.optJSONObject("probabilities")
                    if (probsObj != null) {
                        for (pKey in probsObj.keys()) {
                            probsMap[pKey] = probsObj.optDouble(pKey, 0.0).toFloat()
                        }
                    } else {
                        val probsArr = aObj.optJSONArray("probabilities")
                        if (probsArr != null) {
                            for (i in 0 until probsArr.length()) {
                                probsMap[i.toString()] = probsArr.optDouble(i, 0.0).toFloat()
                            }
                        }
                    }
                    val bestLevel = scoreVal.toInt().coerceIn(0, (question.criteria.size - 1).coerceAtLeast(0))
                    val conf = aObj.optDouble("confidence", 0.8).toFloat()
                    parsedAnswers[key] = DecisionAnswer.ScoreAnswer(
                        scoreIndex = bestLevel,
                        scoreValue = scoreVal,
                        probabilities = probsMap,
                        probability = probsMap[bestLevel.toString()] ?: 0.5f,
                        confidence = conf,
                    )
                }
            }
        }

        val latency = System.currentTimeMillis() - startTime
        DecisionResult(
            answers = parsedAnswers,
            latencyMs = latency,
        )
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/decisions"
    }
}
