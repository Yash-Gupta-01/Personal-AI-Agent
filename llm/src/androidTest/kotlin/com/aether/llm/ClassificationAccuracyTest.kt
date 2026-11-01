package com.aether.llm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.domain.memory.MemoryClassifier
import com.aether.domain.memory.MemoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * Measures classification quality against notes with known answers.
 *
 * "Does it produce a label" is not the question — a broken prompt produces a
 * label too, just the same one every time. This asks whether the label is
 * right, over enough notes that the answer means something.
 *
 * **Six notes per type, deliberately.** An unbalanced set rewards a prompt that
 * simply favours whichever type is most common in it, which is the exact
 * failure being hunted: every bad version of this prompt had one label acting
 * as a catch-all.
 *
 * **Messy in form, unambiguous in intent.** Real captures are lowercase,
 * unpunctuated and abbreviated, so the set is too. What it avoids is notes a
 * careful human would label differently — those measure the labeller's opinion
 * rather than the model, and they make a regression indistinguishable from a
 * disagreement.
 */
@RunWith(AndroidJUnit4::class)
class ClassificationAccuracyTest {

    private lateinit var modelStore: ModelStore
    private lateinit var client: LlamaCppClient

    @Before
    fun setUp() {
        modelStore = ModelStore(ApplicationProvider.getApplicationContext())
        client = LlamaCppClient(modelStore, Dispatchers.Default)
    }

    @After
    fun tearDown() = client.close()

    private val cases: List<Pair<String, MemoryType>> = listOf(
        "priya got promoted to senior designer last week" to MemoryType.PERSON,
        "amma's knee surgery is scheduled for the 14th" to MemoryType.PERSON,
        "rohit and neha are moving in together next month" to MemoryType.PERSON,
        "dad retired after 32 years at the bank" to MemoryType.PERSON,
        "my sister's baby is due in march" to MemoryType.PERSON,
        "meera is learning spanish for her berlin move" to MemoryType.PERSON,

        "postgres vacuum reclaims dead tuples" to MemoryType.FACT,
        "kotlin data classes generate equals and hashcode" to MemoryType.FACT,
        "room uses wal mode by default on android" to MemoryType.FACT,
        "utc has no daylight saving" to MemoryType.FACT,
        "compose recomposition skips unchanged params" to MemoryType.FACT,
        "an arm64 phone always has neon but not always i8mm" to MemoryType.FACT,

        "slept only 4 hours again" to MemoryType.HEALTH,
        "bp was 140 over 90 at the checkup" to MemoryType.HEALTH,
        "knee hurts after running on concrete" to MemoryType.HEALTH,
        "started vitamin d supplements" to MemoryType.HEALTH,
        "resting heart rate down to 58" to MemoryType.HEALTH,
        "doctor said my iron is low" to MemoryType.HEALTH,

        "got moved to the platform team" to MemoryType.CAREER,
        "manager wants me to lead the migration" to MemoryType.CAREER,
        "thinking of switching from frontend to backend" to MemoryType.CAREER,
        "my new role needs kubernetes" to MemoryType.CAREER,
        "appraisal cycle starts in november" to MemoryType.CAREER,
        "offered a senior position at the fintech startup" to MemoryType.CAREER,

        "aether is my offline first assistant app" to MemoryType.PROJECT,
        "rewriting the invoice tool in rust" to MemoryType.PROJECT,
        "the scraper needs a retry queue" to MemoryType.PROJECT,
        "building keyboard firmware for the split board" to MemoryType.PROJECT,
        "portfolio site is half done" to MemoryType.PROJECT,
        "adding dark mode to the budget tracker" to MemoryType.PROJECT,

        "i like my tea without sugar" to MemoryType.PREFERENCE,
        "hate open plan offices" to MemoryType.PREFERENCE,
        "prefer window seats on flights" to MemoryType.PREFERENCE,
        "cant stand coriander" to MemoryType.PREFERENCE,
        "i work better with music off" to MemoryType.PREFERENCE,
        "i'd rather text than call" to MemoryType.PREFERENCE,

        "want to save 2L by december" to MemoryType.GOAL,
        "planning to run a half marathon next spring" to MemoryType.GOAL,
        "need to get to conversational german this year" to MemoryType.GOAL,
        "aiming to read 24 books" to MemoryType.GOAL,
        "want to move out by april" to MemoryType.GOAL,
        "target is 10k monthly side income" to MemoryType.GOAL,

        "i walk 30 mins every evening" to MemoryType.HABIT,
        "journal for ten minutes each night" to MemoryType.HABIT,
        "gym on mon wed fri" to MemoryType.HABIT,
        "i check email first thing every morning" to MemoryType.HABIT,
        "sunday is always meal prep" to MemoryType.HABIT,
        "i reread my notes every friday" to MemoryType.HABIT,

        "felt drained after standup today" to MemoryType.REFLECTION,
        "been avoiding hard problems lately" to MemoryType.REFLECTION,
        "i get anxious before demos" to MemoryType.REFLECTION,
        "felt really good after shipping that fix" to MemoryType.REFLECTION,
        "i keep second guessing myself in reviews" to MemoryType.REFLECTION,
        "today was a slog" to MemoryType.REFLECTION,
    )

    @Test
    // Well past the ~2.5 min this takes: runTest defaults to 60s, which is
    // fine for a fake and useless for real inference over a real set.
    fun classifiesRealisticNotesCorrectly() = runTest(timeout = 10.minutes) {
        assumeTrue("no model downloaded", modelStore.isModelPresent())

        val results = cases.map { (note, expected) ->
            val raw = client.generate(MemoryClassifier.buildPrompt(note), MemoryClassifier.MAX_TOKENS)
            Triple(note, expected, MemoryClassifier.parse(raw))
        }
        val correct = results.count { (_, expected, actual) -> expected == actual }

        // Per type, so a prompt change that lifts one label by sinking another
        // is visible rather than hiding inside a single percentage.
        cases.map { it.second }.distinct().forEach { type ->
            val forType = results.filter { it.second == type }
            android.util.Log.i(
                "AetherAcc",
                "${type.name}: ${forType.count { it.second == it.third }}/${forType.size}" +
                    " lost to " + forType.filter { it.second != it.third }
                    .groupingBy { it.third.name }.eachCount(),
            )
        }
        results.filter { it.second != it.third }.forEach { (note, expected, actual) ->
            android.util.Log.i("AetherAccMiss", "$actual (want $expected) <- $note")
        }
        android.util.Log.i("AetherAcc", "TOTAL $correct/${cases.size}")

        assertTrue(
            "$correct/${cases.size} correct, below the recorded baseline",
            correct >= BASELINE_CORRECT,
        )
    }

    private companion object {
        /**
         * The measured score for LFM2.5-1.2B with the current prompt: 26/54.
         *
         * Low, and honest. The same prompt scored 7/9 on the nine-note set this
         * replaced — n=9 could not tell 78% from 48%, and every prompt
         * conclusion drawn from it was under-powered.
         *
         * Ablations at n=54 put the prompt's remaining headroom at ~3 notes:
         * removing the examples gives 24, removing the decision order gives 23.
         * Three structurally different prompts inside three points is a model
         * ceiling, not a wording problem — so raise this when the *structure*
         * changes (few-shot as real chat turns, label scoring instead of free
         * generation, a larger model), not by rewording.
         *
         * Raise it whenever a change is confirmed better, so the ratchet only
         * turns one way. Output is deterministic run to run, so this guards
         * regressions rather than being a threshold padded with slack.
         */
        const val BASELINE_CORRECT = 26
    }
}
