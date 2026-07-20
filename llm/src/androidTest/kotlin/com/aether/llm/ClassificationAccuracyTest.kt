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

/**
 * Measures classification quality against notes with obvious answers.
 *
 * "Does it produce a label" is not the question — a broken prompt produces a
 * label too, just always the first one listed. This asks whether the label is
 * right, over enough notes that an order bias cannot hide.
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

    /** Note to the types a reasonable person would accept for it. */
    private val cases = listOf(
        "I prefer working late at night and hate early meetings" to setOf(MemoryType.PREFERENCE),
        "I want to run a half marathon next spring" to setOf(MemoryType.GOAL),
        "I go for a walk every morning before work" to setOf(MemoryType.HABIT),
        "My sister Priya is moving to Bangalore in August" to setOf(MemoryType.PERSON),
        "I am thinking about moving from frontend into backend work" to setOf(MemoryType.CAREER),
        "Aether is my local-first personal assistant app" to setOf(MemoryType.PROJECT),
        "My blood pressure was high at the last checkup" to setOf(MemoryType.HEALTH),
        "Kotlin's trimIndent does nothing if an interpolated block is unindented"
            to setOf(MemoryType.FACT),
        "I have been avoiding hard problems lately and I am not sure why"
            to setOf(MemoryType.REFLECTION),
    )

    @Test
    fun classifiesObviousNotesCorrectly() = runTest {
        assumeTrue("no model downloaded", modelStore.isModelPresent())

        val wrong = cases.mapNotNull { (note, accepted) ->
            val raw = client.generate(MemoryClassifier.buildPrompt(note), MemoryClassifier.MAX_TOKENS)
            val actual = MemoryClassifier.parse(raw)
            android.util.Log.i("AetherAcc", "$actual <- $note (raw='${raw.trim()}')")
            if (actual in accepted) null else "$note -> $actual, expected $accepted"
        }

        // 7/9 is the measured baseline for LFM2.5-1.2B with the current prompt,
        // and output is bit-identical run to run, so this is a real regression
        // guard rather than a threshold with slack in it. Prompt work that
        // trips this has traded one category's accuracy for another's — which
        // is exactly the thing that is invisible without a fixed set.
        assertTrue(
            "${wrong.size}/${cases.size} misclassified (baseline is 2):\n" +
                wrong.joinToString("\n"),
            wrong.size <= 2,
        )
    }
}
