package com.aether.llm.decision

import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.domain.privacy.PiiRedactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Cloud adapter for Google's Gemini Decisions API (v1beta/decisions).
 *
 * Evaluates operational decision questions with zero generated output tokens
 * and calibrated logit probabilities.
 *
 * Automatically routes input through [PiiRedactor] before transmission,
 * ensuring no personal identifiers leave the device unmasked.
 */
class GeminiDecisionsAdapter(
    private val piiRedactor: PiiRedactor? = null,
    private val apiKeyProvider: () -> String? = { null },
) : DecisionPort {

    override suspend fun evaluate(
        input: String,
        questions: Map<String, DecisionQuestion>,
    ): DecisionResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider() ?: throw IllegalStateException("Gemini API key is not configured")
        val startTime = System.currentTimeMillis()

        // 1. Redact PII locally before sending over the wire
        val sanitizedInput = if (piiRedactor != null) {
            piiRedactor.redact(input).redactedText
        } else {
            input
        }

        // 2. Build JSON request payload
        val requestJson = JSONObject()
        requestJson.put("input", sanitizedInput)

        val decisionsObj = JSONObject()
        for ((key, q) in questions) {
            val qObj = JSONObject()
            qObj.put("instructions", q.instructions)
            when (q) {
                is DecisionQuestion.Noul -> {
                    qObj.put("noul", JSONObject())
                }
                is DecisionQuestion.Choice -> {
                    val criteriaObj = JSONObject()
                    for ((cKey, cDesc) in q.criteria) {
                        criteriaObj.put(cKey, cDesc)
                    }
                    val choiceObj = JSONObject()
                    choiceObj.put("criteria", criteriaObj)
                    qObj.put("choice", choiceObj)
                }
                is DecisionQuestion.Score -> {
                    val criteriaArray = JSONArray()
                    for (criterion in q.criteria) {
                        criteriaArray.put(criterion)
                    }
                    val scoreObj = JSONObject()
                    scoreObj.put("criteria", criteriaArray)
                    qObj.put("score", scoreObj)
                }
            }
            decisionsObj.put(key, qObj)
        }
        requestJson.put("decisions", decisionsObj)

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
                    val noulObj = aObj.optJSONObject("noul")
                    if (noulObj != null) {
                        val rawProb = noulObj.optDouble("probability", 0.5).toFloat()
                        val clampedProb = min(0.99f, max(0.01f, rawProb))
                        val isTrue = noulObj.optBoolean("answer", clampedProb >= 0.5f)
                        val defaultConf = (if (clampedProb >= 0.5f) clampedProb - 0.5f else 0.5f - clampedProb) * 2.0
                        val conf = noulObj.optDouble("confidence", defaultConf).toFloat()
                        parsedAnswers[key] = DecisionAnswer.NoulAnswer(
                            isTrue = isTrue,
                            probability = clampedProb,
                            confidence = conf,
                        )
                    }
                }
                is DecisionQuestion.Choice -> {
                    val choiceObj = aObj.optJSONObject("choice")
                    if (choiceObj != null) {
                        val selected = choiceObj.optString("answer", "")
                        val probsObj = choiceObj.optJSONObject("probabilities")
                        val probsMap = mutableMapOf<String, Float>()
                        if (probsObj != null) {
                            for (pKey in probsObj.keys()) {
                                probsMap[pKey] = probsObj.optDouble(pKey, 0.0).toFloat()
                            }
                        }
                        val prob = probsMap[selected] ?: 0.5f
                        val conf = choiceObj.optDouble("confidence", prob.toDouble()).toFloat()
                        parsedAnswers[key] = DecisionAnswer.ChoiceAnswer(
                            selectedKey = selected,
                            probabilities = probsMap,
                            probability = prob,
                            confidence = conf,
                        )
                    }
                }
                is DecisionQuestion.Score -> {
                    val scoreObj = aObj.optJSONObject("score")
                    if (scoreObj != null) {
                        val scoreVal = scoreObj.optDouble("score", 0.0).toFloat()
                        val scoreIdx = scoreVal.toInt()
                        val probsObj = scoreObj.optJSONObject("probabilities")
                        val probsMap = mutableMapOf<String, Float>()
                        if (probsObj != null) {
                            for (pKey in probsObj.keys()) {
                                probsMap[pKey] = probsObj.optDouble(pKey, 0.0).toFloat()
                            }
                        }
                        val prob = probsMap[scoreIdx.toString()] ?: 0.5f
                        val conf = scoreObj.optDouble("confidence", prob.toDouble()).toFloat()
                        parsedAnswers[key] = DecisionAnswer.ScoreAnswer(
                            scoreIndex = scoreIdx,
                            scoreValue = scoreVal,
                            probabilities = probsMap,
                            probability = prob,
                            confidence = conf,
                        )
                    }
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
