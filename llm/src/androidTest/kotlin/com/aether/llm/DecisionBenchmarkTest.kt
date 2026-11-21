package com.aether.llm

import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.domain.privacy.CloudSafeDecisionInput
import com.aether.llm.decision.GeminiDecisionsAdapter
import com.aether.llm.decision.HybridDecisionAdapter
import com.aether.llm.decision.LocalJevDecisionAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Comprehensive 50-Case On-Device Operational Benchmark.
 *
 * Compares 3 execution methods over identical operational queries:
 * 1. Method A: Deterministic Operational Fallback
 * 2. Method B: Local Jev Model (jevos-v4 GGUF via single forward-pass logit scoring)
 * 3. Method C: Cloud Gemini Decisions API (v1beta/decisions with client-side PII redaction)
 */
@RunWith(AndroidJUnit4::class)
class DecisionBenchmarkTest {

    private lateinit var fallbackAdapter: DecisionPort
    private lateinit var localJevAdapter: LocalJevDecisionAdapter
    private var geminiAdapter: GeminiDecisionsAdapter? = null

    data class OperationalTestCase(
        val id: Int,
        val input: String,
        val expectedIsFriction: Boolean,
        val expectedThread: String, // "gym", "sleep", "work", "health", "none"
        val expectedActionable: Boolean,
        val category: String,
    )

    // Exact 50-case benchmark dataset:
    // 30 Friction cases (Gym, Sleep, Work/Health) + 20 Silence cases (Wins, Facts, Reflections)
    private val benchmark50Dataset = listOf(
        // === Group 1: Gym / Fitness Friction (10 cases) ===
        OperationalTestCase(1, "didn't get to go to gym today", true, "gym", true, "Gym Friction"),
        OperationalTestCase(2, "missed gym again because i overslept", true, "gym", true, "Gym Friction"),
        OperationalTestCase(3, "couldn't make it to gym again today", true, "gym", true, "Gym Friction"),
        OperationalTestCase(4, "skipped workout today feeling lazy", true, "gym", true, "Gym Friction"),
        OperationalTestCase(5, "was unable to go to gym this morning", true, "gym", true, "Gym Friction"),
        OperationalTestCase(6, "missed leg day because meeting ran over", true, "gym", true, "Gym Friction"),
        OperationalTestCase(7, "failed to get my workout in before dinner", true, "gym", true, "Gym Friction"),
        OperationalTestCase(8, "wanted to go for a run but stayed on the couch", true, "gym", true, "Gym Friction"),
        OperationalTestCase(9, "skipped the gym third time this week", true, "gym", true, "Gym Friction"),
        OperationalTestCase(10, "could not finish my exercises feeling drained", true, "gym", true, "Gym Friction"),

        // === Group 2: Sleep & Bedtime Friction (10 cases) ===
        OperationalTestCase(11, "stayed up until 3am browsing reddit again", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(12, "slept only 4 hours feeling terrible", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(13, "couldn't fall asleep until past midnight", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(14, "woke up exhausted and groggy", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(15, "scrolling in bed past midnight again", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(16, "insomnia again tonight tossing and turning", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(17, "went to bed at 2am and missed my alarm", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(18, "binged youtube in bed until 4 in the morning", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(19, "sleep schedule is completely ruined", true, "sleep", true, "Sleep Friction"),
        OperationalTestCase(20, "closed the alarm and fell back asleep for 2 hours", true, "sleep", true, "Sleep Friction"),

        // === Group 3: Work & Health Routine Friction (10 cases) ===
        OperationalTestCase(21, "couldn't finish the report before eod deadline", true, "work", true, "Work Friction"),
        OperationalTestCase(22, "missed the project standup meeting", true, "work", true, "Work Friction"),
        OperationalTestCase(23, "forgot to take blood pressure medicine today", true, "health", true, "Health Friction"),
        OperationalTestCase(24, "skipped lunch and feel a terrible headache coming", true, "health", true, "Health Friction"),
        OperationalTestCase(25, "didn't take my vitamins this morning", true, "health", true, "Health Friction"),
        OperationalTestCase(26, "failed to submit sprint timesheet on time", true, "work", true, "Work Friction"),
        OperationalTestCase(27, "drank zero water today feeling completely dehydrated", true, "health", true, "Health Friction"),
        OperationalTestCase(28, "couldn't focus at work distracted all afternoon", true, "work", true, "Work Friction"),
        OperationalTestCase(29, "forgot to log my expenses for the client dinner", true, "work", true, "Work Friction"),
        OperationalTestCase(30, "skipped breakfast again rushing out the door", true, "health", true, "Health Friction"),

        // === Group 4: Positive Wins / Routine Successes (10 cases) -> MUST BE SILENT ===
        OperationalTestCase(31, "went to gym and crushed my deadlift PR", false, "none", false, "Win (Silence)"),
        OperationalTestCase(32, "completed morning run 5k in 24 mins", false, "none", false, "Win (Silence)"),
        OperationalTestCase(33, "had 8 hours of solid deep sleep feeling energized", false, "none", false, "Win (Silence)"),
        OperationalTestCase(34, "woke up on time at 6:30am feeling fresh", false, "none", false, "Win (Silence)"),
        OperationalTestCase(35, "finished all sprint tickets early", false, "none", false, "Win (Silence)"),
        OperationalTestCase(36, "hit all my protein goals for the day", false, "none", false, "Win (Silence)"),
        OperationalTestCase(37, "meditated for 20 minutes feeling peaceful", false, "none", false, "Win (Silence)"),
        OperationalTestCase(38, "delivered the presentation without any hiccups", false, "none", false, "Win (Silence)"),
        OperationalTestCase(39, "drank 3 liters of water today feeling great", false, "none", false, "Win (Silence)"),
        OperationalTestCase(40, "hit 10000 steps before 5pm", false, "none", false, "Win (Silence)"),

        // === Group 5: Factual / Informational / Passive Notes (10 cases) -> MUST BE SILENT ===
        OperationalTestCase(41, "postgres vacuum reclaims dead tuples", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(42, "kotlin data classes generate equals and hashcode", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(43, "room uses wal mode by default on android", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(44, "utc has no daylight saving", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(45, "priya got promoted to senior designer last week", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(46, "amma knee surgery scheduled for the 14th", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(47, "i like my tea without sugar", false, "none", false, "Preference (Silence)"),
        OperationalTestCase(48, "prefer window seats on flights", false, "none", false, "Preference (Silence)"),
        OperationalTestCase(49, "aether is an offline first assistant app", false, "none", false, "Fact (Silence)"),
        OperationalTestCase(50, "planning to run a marathon next spring", false, "none", false, "Thought (Silence)"),
    )

    private val operationalQuestions = mapOf(
        "is_friction" to DecisionQuestion.Noul(
            instructions = "Does this capture indicate friction, a missed commitment, obstacle, or routine struggle?",
        ),
        "thread" to DecisionQuestion.Choice(
            instructions = "Which routine thread does this capture belong to?",
            criteria = mapOf(
                "gym" to "Exercise, workout, run, or fitness sessions",
                "sleep" to "Sleep schedule, insomnia, late nights, waking up late, or fatigue",
                "work" to "Job, tasks, deadlines, sprint tickets, or office meetings",
                "health" to "Diet, hydration, medication, vitamins, or wellness",
                "none" to "General facts, successes, positive thoughts, or non-routine items",
            ),
        ),
        "actionability" to DecisionQuestion.Score(
            instructions = "Rate the immediate actionability of this note from 0 to 3",
            criteria = listOf(
                "Pure passive fact, success, or observation (no action)",
                "Vague thought with no clear immediate intervention",
                "Actionable situation with plausible proactive step",
                "Critical missed routine with immediate high-leverage action",
            ),
        ),
    )

    @Before
    fun setUp() {
        localJevAdapter = LocalJevDecisionAdapter()

        // Check if Jev GGUF is available in standard app storage or /data/local/tmp
        val jevPathCandidates = listOf(
            "/data/local/tmp/jevos-v4-q4_k_m.gguf",
            File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "models/jevos-v4-q4_k_m.gguf").absolutePath,
        )
        for (path in jevPathCandidates) {
            if (File(path).exists()) {
                val loaded = localJevAdapter.loadModel(path)
                Log.i("AetherBenchmark", "Jev model at $path loaded = $loaded")
                if (loaded) break
            }
        }

        // Setup Fallback adapter
        fallbackAdapter = HybridDecisionAdapter(
            localAdapter = LocalJevDecisionAdapter(), // un-loaded
            geminiAdapter = GeminiDecisionsAdapter(apiKeyProvider = { null }),
        )

        // Setup Gemini adapter if API key was injected via .env / local.properties / BuildConfig
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank()) {
            geminiAdapter = GeminiDecisionsAdapter(
                apiKeyProvider = { apiKey },
            )
        }
    }

    @Test
    fun benchmarkMethodA_DeterministicFallback() = runTest {
        runBenchmarkSuite("Method A (Deterministic Fallback)", fallbackAdapter)
    }

    @Test
    fun benchmarkMethodB_LocalJevModel() = runTest {
        if (!localJevAdapter.isModelLoaded) {
            Log.w("AetherBenchmark", "SKIPPED: Method B (Local Jev) — jevos-v4-q4_k_m.gguf not found on device.")
            Log.w("AetherBenchmark", "To run Jev: adb push jevos-v4-q4_k_m.gguf /data/local/tmp/")
            return@runTest
        }
        runBenchmarkSuite("Method B (Local Jev GGUF Single-Pass)", localJevAdapter)
    }

    @Test
    fun benchmarkMethodC_GeminiDecisionsApi() = runTest {
        val adapter = geminiAdapter
        if (adapter == null) {
            Log.w("AetherBenchmark", "SKIPPED: Method C (Gemini Decisions API) — No API key in .env or local.properties.")
            Log.w("AetherBenchmark", "To run Gemini: Add GEMINI_API_KEY=AIza... to .env in project root.")
            return@runTest
        }
        val portWrapper = object : DecisionPort {
            override suspend fun evaluate(input: String, questions: Map<String, DecisionQuestion>): DecisionResult {
                return adapter.evaluate(CloudSafeDecisionInput.createForTesting(input), questions)
            }
        }
        runBenchmarkSuite("Method C (Gemini Decisions API with PII Redaction)", portWrapper)
    }

    private suspend fun runBenchmarkSuite(methodName: String, port: DecisionPort) {
        val memBefore = Debug.MemoryInfo()
        Debug.getMemoryInfo(memBefore)

        val latencies = mutableListOf<Long>()
        var correctFrictionCount = 0
        var correctThreadCount = 0
        var correctSilenceCount = 0
        val totalSilenceCases = benchmark50Dataset.count { !it.expectedIsFriction }
        val totalFrictionCases = benchmark50Dataset.count { it.expectedIsFriction }

        Log.i("AetherBenchmark", "==================================================")
        Log.i("AetherBenchmark", "STARTING BENCHMARK: $methodName (50 Cases)")
        Log.i("AetherBenchmark", "==================================================")

        for (case in benchmark50Dataset) {
            val result: DecisionResult
            val elapsed = measureTimeMillis {
                result = port.evaluate(case.input, operationalQuestions)
            }
            latencies.add(elapsed)

            val frictionAns = result.answers["is_friction"] as? DecisionAnswer.NoulAnswer
            val threadAns = result.answers["thread"] as? DecisionAnswer.ChoiceAnswer

            val detectedFriction = frictionAns?.isTrue == true
            val detectedThread = threadAns?.selectedKey ?: "none"

            if (detectedFriction == case.expectedIsFriction) {
                correctFrictionCount++
            }

            if (!case.expectedIsFriction) {
                if (!detectedFriction || detectedThread == "none") {
                    correctSilenceCount++
                }
            } else {
                if (detectedThread == case.expectedThread) {
                    correctThreadCount++
                }
            }
        }

        val memAfter = Debug.MemoryInfo()
        Debug.getMemoryInfo(memAfter)

        latencies.sort()
        val p50 = latencies[latencies.size / 2]
        val p95 = latencies[(latencies.size * 0.95).toInt()]
        val frictionAcc = (correctFrictionCount.toDouble() / benchmark50Dataset.size) * 100.0
        val threadAcc = (correctThreadCount.toDouble() / totalFrictionCases) * 100.0
        val silenceRate = (correctSilenceCount.toDouble() / totalSilenceCases) * 100.0
        val nativePssDelta = memAfter.nativePss - memBefore.nativePss

        Log.i("AetherBenchmark", "--- RESULTS FOR $methodName ---")
        Log.i("AetherBenchmark", "Total Dataset: ${benchmark50Dataset.size} cases (30 Friction, 20 Silence)")
        Log.i("AetherBenchmark", "Friction Detection Accuracy: %.1f%% (%d/%d)".format(frictionAcc, correctFrictionCount, benchmark50Dataset.size))
        Log.i("AetherBenchmark", "Thread Routing Accuracy:     %.1f%% (%d/%d)".format(threadAcc, correctThreadCount, totalFrictionCases))
        Log.i("AetherBenchmark", "Correct Silence Rate:        %.1f%% (%d/%d)".format(silenceRate, correctSilenceCount, totalSilenceCases))
        Log.i("AetherBenchmark", "Inference Latency:           p50 = ${p50}ms, p95 = ${p95}ms")
        Log.i("AetherBenchmark", "Native Memory Delta:         ${nativePssDelta} KB")
        Log.i("AetherBenchmark", "==================================================")
    }
}
