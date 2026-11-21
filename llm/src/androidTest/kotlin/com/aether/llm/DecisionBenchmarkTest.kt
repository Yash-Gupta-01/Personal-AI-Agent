package com.aether.llm

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.domain.decision.DecisionAnswer
import com.aether.domain.decision.DecisionPort
import com.aether.domain.decision.DecisionQuestion
import com.aether.domain.decision.DecisionResult
import com.aether.llm.decision.HybridDecisionAdapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * On-device benchmark test for operational decision models (M3.3).
 *
 * Compares System 1 operational decision gates:
 * 1. Operational Accuracy: Does it identify friction and route to the correct thread?
 * 2. Correct Silence Rate: Does it remain silent (friction = false / actionability = 0) on non-friction notes?
 * 3. Latency: Single-pass latency distribution (p50 / p95).
 * 4. Memory Footprint: Native PSS and Dalvik PSS tracking.
 */
@RunWith(AndroidJUnit4::class)
class DecisionBenchmarkTest {

    private lateinit var decisionPort: DecisionPort

    @Before
    fun setUp() {
        val local = com.aether.llm.decision.LocalJevDecisionAdapter()
        val gemini = com.aether.llm.decision.GeminiDecisionsAdapter(piiRedactor = null, apiKeyProvider = { null })
        decisionPort = HybridDecisionAdapter(
            localAdapter = local,
            geminiAdapter = gemini,
        )
    }

    data class OperationalTestCase(
        val input: String,
        val expectedIsFriction: Boolean,
        val expectedThread: String, // "gym", "sleep", "work", "health", "none"
        val expectedActionable: Boolean,
    )

    private val benchmarkDataset = listOf(
        // --- Gym friction (Actionable) ---
        OperationalTestCase("didn't get to go to gym today", true, "gym", true),
        OperationalTestCase("missed gym again because i overslept", true, "gym", true),
        OperationalTestCase("couldn't make it to gym again today", true, "gym", true),
        OperationalTestCase("skipped workout today feeling lazy", true, "gym", true),
        OperationalTestCase("was unable to go to gym this morning", true, "gym", true),

        // --- Sleep friction (Actionable) ---
        OperationalTestCase("stayed up until 3am browsing reddit again", true, "sleep", true),
        OperationalTestCase("slept only 4 hours feeling terrible", true, "sleep", true),
        OperationalTestCase("couldn't fall asleep until late", true, "sleep", true),
        OperationalTestCase("woke up exhausted and groggy", true, "sleep", true),
        OperationalTestCase("scrolling in bed past midnight again", true, "sleep", true),

        // --- Work / Health friction ---
        OperationalTestCase("couldn't finish the report before eod deadline", true, "work", true),
        OperationalTestCase("missed the project standup meeting", true, "work", true),
        OperationalTestCase("forgot to take blood pressure medicine today", true, "health", true),
        OperationalTestCase("skipped lunch and feel a headache coming", true, "health", true),
        OperationalTestCase("didn't take my vitamins this morning", true, "health", true),

        // --- Success / Positive notes (MUST BE SILENT: friction = false) ---
        OperationalTestCase("went to gym and crushed my deadlift PR", false, "none", false),
        OperationalTestCase("completed morning run 5k in 24 mins", false, "none", false),
        OperationalTestCase("had 8 hours of solid deep sleep feeling energized", false, "none", false),
        OperationalTestCase("woke up on time at 6:30am feeling fresh", false, "none", false),
        OperationalTestCase("finished all sprint tickets early", false, "none", false),

        // --- Factual / Preference / Other notes (MUST BE SILENT: friction = false) ---
        OperationalTestCase("postgres vacuum reclaims dead tuples", false, "none", false),
        OperationalTestCase("kotlin data classes generate equals and hashcode", false, "none", false),
        OperationalTestCase("room uses wal mode by default on android", false, "none", false),
        OperationalTestCase("utc has no daylight saving", false, "none", false),
        OperationalTestCase("priya got promoted to senior designer last week", false, "none", false),
        OperationalTestCase("amma knee surgery scheduled for the 14th", false, "none", false),
        OperationalTestCase("i like my tea without sugar", false, "none", false),
        OperationalTestCase("prefer window seats on flights", false, "none", false),
        OperationalTestCase("aether is an offline first assistant app", false, "none", false),
        OperationalTestCase("planning to run a marathon next spring", false, "none", false),
    )

    @Test
    fun benchmarkOperationalDecisionsAndRestraint() = runTest {
        val memInfoBefore = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfoBefore)

        val questions = mapOf(
            "is_friction" to DecisionQuestion.Noul(
                instructions = "Does this input indicate friction, a missed routine, or a recurring struggle?",
            ),
            "thread" to DecisionQuestion.Choice(
                instructions = "Which thread does this note belong to?",
                criteria = mapOf(
                    "gym" to "Exercise, workout, or gym routines",
                    "sleep" to "Sleep schedule, insomnia, late nights, or fatigue",
                    "work" to "Job, tasks, deadlines, or office commitments",
                    "health" to "Diet, hydration, medication, or wellness",
                    "none" to "General thoughts, facts, successes, or non-routine items",
                ),
            ),
            "actionability" to DecisionQuestion.Score(
                instructions = "Rate the immediate actionability of this note from 0 (passive info) to 3 (clear proactive action needed)",
                criteria = listOf(
                    "Pure passive fact or observation",
                    "Vague thought with no clear action",
                    "Actionable situation with plausible next step",
                    "Critical missed commitment with immediate high-leverage action",
                ),
            ),
        )

        val latencies = mutableListOf<Long>()
        var correctFrictionCount = 0
        var correctThreadCount = 0
        var correctSilenceCount = 0
        var totalSilenceOpportunities = 0

        for (case in benchmarkDataset) {
            val result: DecisionResult
            val elapsed = measureTimeMillis {
                result = decisionPort.evaluate(case.input, questions)
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
                totalSilenceOpportunities++
                // Silence is achieved if either friction is false OR thread is classified as none
                if (!detectedFriction || detectedThread == "none") {
                    correctSilenceCount++
                }
            } else {
                if (detectedThread == case.expectedThread) {
                    correctThreadCount++
                }
            }
        }

        val memInfoAfter = Debug.MemoryInfo()
        Debug.getMemoryInfo(memInfoAfter)

        latencies.sort()
        val p50 = latencies[latencies.size / 2]
        val p95 = latencies[(latencies.size * 0.95).toInt()]
        val frictionAccuracy = (correctFrictionCount.toDouble() / benchmarkDataset.size) * 100.0
        val silenceRate = (correctSilenceCount.toDouble() / totalSilenceOpportunities) * 100.0

        val nativePssDeltaKb = memInfoAfter.nativePss - memInfoBefore.nativePss
        val totalPssDeltaKb = memInfoAfter.totalPss - memInfoBefore.totalPss

        Log.i("AetherBenchmark", "=== Operational Decision Benchmark Results ===")
        Log.i("AetherBenchmark", "Total Cases: ${benchmarkDataset.size}")
        Log.i("AetherBenchmark", "Friction Detection Accuracy: %.1f%% (%d/%d)".format(frictionAccuracy, correctFrictionCount, benchmarkDataset.size))
        Log.i("AetherBenchmark", "Correct Silence Rate: %.1f%% (%d/%d)".format(silenceRate, correctSilenceCount, totalSilenceOpportunities))
        Log.i("AetherBenchmark", "Latency: p50 = ${p50}ms, p95 = ${p95}ms")
        Log.i("AetherBenchmark", "Memory Delta: Native PSS = ${nativePssDeltaKb}KB, Total PSS = ${totalPssDeltaKb}KB")

        // Assertions for operational viability:
        // Must achieve at least 80% friction accuracy and at least 90% silence on non-friction notes
        assertTrue("Friction accuracy must be >= 80%, was $frictionAccuracy%", frictionAccuracy >= 80.0)
        assertTrue("Correct silence rate must be >= 90%, was $silenceRate%", silenceRate >= 90.0)
    }
}
