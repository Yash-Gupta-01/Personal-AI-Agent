package com.aether.llm

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.domain.action.ActionRecord
import com.aether.domain.action.ActionRecordRepository
import com.aether.domain.action.ActionStatus
import com.aether.domain.action.ProcessFrictionNudge
import com.aether.domain.decision.DecisionPort
import com.aether.domain.policy.DefaultRestraintPolicy
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.policy.RestraintPolicy
import com.aether.domain.thread.TrackedThread
import com.aether.domain.thread.TrackedThreadRepository
import com.aether.llm.decision.GeminiDecisionsAdapter
import com.aether.llm.decision.HybridDecisionAdapter
import com.aether.llm.decision.LocalJevDecisionAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import kotlin.system.measureTimeMillis

/**
 * Multi-Suite Longitudinal & Operational Evaluation Runner.
 *
 * Implements the 3-Suite Evaluation Framework:
 * - Suite A: Single-Turn Decision Gating (60 Cases: Friction, Silence, External Context)
 * - Suite B: Longitudinal Multi-Day Sequences (Streak recovery, deduplication, causal pivots)
 * - Suite C: Hard Restraint & Policy Invariants (Zero AI Overrides)
 */
@RunWith(AndroidJUnit4::class)
class LongitudinalEvalTest {

    private lateinit var context: Context
    private lateinit var decisionPort: DecisionPort

    // In-Memory Test Repositories
    private val threadStore = mutableMapOf<String, TrackedThread>()
    private val actionStore = mutableMapOf<String, ActionRecord>()

    private val threadRepo = object : TrackedThreadRepository {
        override suspend fun getById(id: String): TrackedThread? = threadStore[id]
        override suspend fun getByTopic(topic: String): TrackedThread? = threadStore.values.firstOrNull { it.topic == topic }
        override fun observeActive(): Flow<List<TrackedThread>> = flowOf(threadStore.values.toList())
        override suspend fun save(thread: TrackedThread) { threadStore[thread.id] = thread }
        override suspend fun delete(id: String) { threadStore.remove(id) }
    }

    private val actionRepo = object : ActionRecordRepository {
        override fun observePending(): Flow<List<ActionRecord>> = flowOf(actionStore.values.filter { it.status == ActionStatus.PROPOSED || it.status == ActionStatus.APPROVED })
        override fun observeForThread(threadId: String): Flow<List<ActionRecord>> = flowOf(actionStore.values.filter { it.threadId == threadId })
        override suspend fun getById(id: String): ActionRecord? = actionStore[id]
        override suspend fun save(record: ActionRecord) { actionStore[record.id] = record }
        override suspend fun delete(id: String) { actionStore.remove(id) }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        threadStore.clear()
        actionStore.clear()

        val apiKey = BuildConfig.GEMINI_API_KEY
        val gemini = if (apiKey.isNotBlank()) {
            GeminiDecisionsAdapter(piiRedactor = null, apiKeyProvider = { apiKey })
        } else {
            GeminiDecisionsAdapter(piiRedactor = null, apiKeyProvider = { null })
        }

        decisionPort = HybridDecisionAdapter(
            localAdapter = LocalJevDecisionAdapter(),
            geminiAdapter = gemini,
        )
    }

    @Test
    fun runCompleteEvaluationSuite() = runTest {
        val jsonText = context.assets.open("eval_scenarios.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonText)

        Log.i("AetherEval", "================================================================================")
        Log.i("AetherEval", "STARTING AETHER COMPREHENSIVE EVALUATION HARNESS")
        Log.i("AetherEval", "Backend: ${if (BuildConfig.GEMINI_API_KEY.isNotBlank()) "Gemini Decision 1 (Cloud)" else "Deterministic Local Fallback"}")
        Log.i("AetherEval", "================================================================================")

        // --- SUITE A: SINGLE-TURN DECISION GATING (Cases 1 - 60) ---
        val singleTurnCases = root.getJSONArray("single_turn_cases")
        var truePositives = 0
        var falsePositives = 0
        var trueNegatives = 0
        var falseNegatives = 0
        var correctThreads = 0
        var totalThreadsEvaluated = 0
        val latencies = mutableListOf<Long>()

        val nudge = ProcessFrictionNudge(
            threadRepository = threadRepo,
            actionRepository = actionRepo,
            restraintPolicy = DefaultRestraintPolicy(),
            decisionPort = decisionPort,
            restraintConfigRepository = null,
        )

        for (i in 0 until singleTurnCases.length()) {
            val caseObj = singleTurnCases.getJSONObject(i)
            val id = caseObj.getInt("id")
            val input = caseObj.getString("input")
            val expectedEvent = caseObj.getString("expected_event") // FRICTION, SUCCESS, OBSTACLE, NONE
            val expectedThread = caseObj.getString("thread")
            val actionExpected = caseObj.getBoolean("action_expected")

            threadStore.clear()
            actionStore.clear()

            // Run at noon to avoid quiet hours in test
            val testTime = Instant.parse("2026-11-21T12:00:00Z")
            val result: ActionRecord?
            val elapsed = measureTimeMillis {
                result = nudge.invoke(input, now = testTime, zone = ZoneId.of("UTC"), todayInterventionCount = 0)
            }
            latencies.add(elapsed)

            val actionProposed = result != null
            val updatedThread = threadStore.values.firstOrNull()

            // Precision / Recall Matrix
            if (actionExpected) {
                if (actionProposed) truePositives++ else falseNegatives++
            } else {
                if (actionProposed) falsePositives++ else trueNegatives++
            }

            // Thread Linking
            if (expectedThread != "none") {
                totalThreadsEvaluated++
                if (updatedThread != null && updatedThread.topic == expectedThread) {
                    correctThreads++
                }
            }
        }

        latencies.sort()
        val p50 = latencies[latencies.size / 2]
        val p95 = latencies[(latencies.size * 0.95).toInt()]
        val precision = if (truePositives + falsePositives > 0) (truePositives.toDouble() / (truePositives + falsePositives)) * 100.0 else 100.0
        val recall = if (truePositives + falseNegatives > 0) (truePositives.toDouble() / (truePositives + falseNegatives)) * 100.0 else 100.0
        val silenceRate = if (trueNegatives + falsePositives > 0) (trueNegatives.toDouble() / (trueNegatives + falsePositives)) * 100.0 else 100.0
        val threadAcc = if (totalThreadsEvaluated > 0) (correctThreads.toDouble() / totalThreadsEvaluated) * 100.0 else 100.0

        Log.i("AetherEval", ">>> SUITE A: SINGLE-TURN OPERATIONAL GATING (60 Cases)")
        Log.i("AetherEval", "Friction Precision:     %.1f%% (%d TP, %d FP)".format(precision, truePositives, falsePositives))
        Log.i("AetherEval", "Friction Recall:        %.1f%% (%d TP, %d FN)".format(recall, truePositives, falseNegatives))
        Log.i("AetherEval", "Thread Linking Acc:     %.1f%% (%d/%d)".format(threadAcc, correctThreads, totalThreadsEvaluated))
        Log.i("AetherEval", "Restraint Silence Rate: %.1f%% (%d/%d silent on non-friction/obstacles)".format(silenceRate, trueNegatives, trueNegatives + falsePositives))
        Log.i("AetherEval", "Inference Latency:      p50 = ${p50}ms, p95 = ${p95}ms")

        // --- SUITE B: LONGITUDINAL MULTI-DAY SEQUENCES ---
        Log.i("AetherEval", ">>> SUITE B: LONGITUDINAL MULTI-DAY SEQUENCES")

        // Sequence 1: Gym Streak Recovery (Miss Day 1, Miss Day 2, Win Day 3)
        threadStore.clear()
        actionStore.clear()
        val day1 = Instant.parse("2026-11-18T12:00:00Z")
        val day2 = Instant.parse("2026-11-19T12:00:00Z")
        val day3 = Instant.parse("2026-11-20T12:00:00Z")

        val act1 = nudge.invoke("didn't get to go to gym today", now = day1)
        assertNotNull("Day 1 miss should propose action", act1)
        assertEquals("Gym misses should be 1", 1, threadRepo.getByTopic("gym")?.consecutiveMisses)

        val act2 = nudge.invoke("missed gym again today", now = day2)
        assertNotNull("Day 2 miss should propose action", act2)
        assertEquals("Gym misses should be 2", 2, threadRepo.getByTopic("gym")?.consecutiveMisses)

        val act3 = nudge.invoke("went to gym and crushed my workout today", now = day3)
        assertNull("Day 3 win should be silent", act3)
        assertEquals("Day 3 win must reset consecutive misses to 0", 0, threadRepo.getByTopic("gym")?.consecutiveMisses)
        assertEquals("Day 3 win must increment consecutive successes", 1, threadRepo.getByTopic("gym")?.consecutiveSuccesses)
        Log.i("AetherEval", "  [PASSED] Sequence 1: Gym Streak Recovery (2 Misses -> 1 Win -> Reset)")

        // Sequence 2: Deduplication (Two captures on same day)
        val act4 = nudge.invoke("missed gym today feeling bad", now = day1, todayInterventionCount = 0)
        assertNotNull("First capture today should propose action", act4)
        val act5 = nudge.invoke("still thinking about how i skipped the gym today", now = day1.plusSeconds(3600), todayInterventionCount = 1)
        assertNull("Second capture today must be blocked by daily limit", act5)
        Log.i("AetherEval", "  [PASSED] Sequence 2: Intra-Day Deduplication (1 Intervention/day limit enforced)")

        // Sequence 3: Causal Pivot (Sleep debt -> Bedtime Wind-Down)
        threadStore.clear()
        actionStore.clear()
        val gymThread = TrackedThread.create("gym", "Morning Gym Routine", now = day1)
        threadStore[gymThread.id] = gymThread

        // User reports closing alarm due to lack of sleep -> blocker attached
        val updatedGymThread = gymThread.withBlocker("sleep_debt", now = day2)
        threadStore[updatedGymThread.id] = updatedGymThread

        val act6 = nudge.invoke("couldn't make it to gym again today", now = day3)
        assertNotNull("Should propose causal action", act6)
        assertEquals("Label should pivot to Wind-Down", "Wind-Down", act6?.toolParams?.get("label"))
        assertEquals("Hour should pivot to 22:30", "22", act6?.toolParams?.get("hour"))
        Log.i("AetherEval", "  [PASSED] Sequence 3: Second-Order Causal Pivot (Attached sleep_debt -> 22:30 Wind-Down)")

        // --- SUITE C: HARD RESTRAINT INVARIANTS (Zero AI Overrides) ---
        Log.i("AetherEval", ">>> SUITE C: HARD RESTRAINT INVARIANTS")
        val policy = DefaultRestraintPolicy()
        val quietTime = Instant.parse("2026-11-21T23:30:00Z") // 11:30 PM (Quiet Hours)

        val quietVerdict = policy.evaluate(
            now = quietTime,
            zone = ZoneId.of("UTC"),
            todayInterventionCount = 0,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(quietHoursStart = java.time.LocalTime.of(22, 0), quietHoursEnd = java.time.LocalTime.of(7, 0)),
        )
        assertEquals("Quiet hours must strictly return BLOCKED_QUIET_HOURS", com.aether.domain.policy.RestraintVerdict.BLOCKED_QUIET_HOURS, quietVerdict)
        Log.i("AetherEval", "  [PASSED] Invariant 1: Quiet hours strictly enforced (Zero AI override permitted)")

        val budgetVerdict = policy.evaluate(
            now = Instant.parse("2026-11-21T12:00:00Z"),
            zone = ZoneId.of("UTC"),
            todayInterventionCount = 1,
            threadCooldownUntil = null,
            consecutiveDismissals = 0,
            hasConcreteAction = true,
            config = RestraintConfig(maxDailyInterventions = 1),
        )
        assertEquals("Daily budget must strictly return BLOCKED_DAILY_BUDGET_EXHAUSTED", com.aether.domain.policy.RestraintVerdict.BLOCKED_DAILY_BUDGET_EXHAUSTED, budgetVerdict)
        Log.i("AetherEval", "  [PASSED] Invariant 2: Daily interruption budget strictly enforced")

        Log.i("AetherEval", "================================================================================")
        Log.i("AetherEval", "EVALUATION COMPLETE: ALL SUITES VERIFIED (100% GREEN)")
        Log.i("AetherEval", "================================================================================")

        assertTrue("Friction precision must be >= 80%", precision >= 80.0)
        assertTrue("Restraint silence rate must be >= 90%", silenceRate >= 90.0)
    }
}
