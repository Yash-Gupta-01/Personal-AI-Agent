package com.aether.llm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aether.domain.llm.LlmUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises real inference against a real model.
 *
 * Every test that needs a model is guarded by `assumeTrue`, so on a device with
 * no model downloaded they report as skipped rather than failed. A fresh clone
 * must not have a red suite for the entirely expected reason that a ~1GB file
 * is absent — that trains people to ignore failures.
 *
 * To run them: download a GGUF and push it into place, e.g.
 *   adb push model.gguf /sdcard/model.gguf
 *   adb shell run-as com.aether.llm.test cp /sdcard/model.gguf files/models/model.gguf
 */
@RunWith(AndroidJUnit4::class)
class LlamaCppClientTest {

    private lateinit var modelStore: ModelStore
    private lateinit var client: LlamaCppClient

    @Before
    fun setUp() {
        modelStore = ModelStore(ApplicationProvider.getApplicationContext())
        client = LlamaCppClient(modelStore, Dispatchers.Default)

        // Logged because "test skipped" and "model missing" look identical in
        // the runner output, and the difference is the whole question when the
        // inference tests silently do nothing.
        android.util.Log.i(
            "AetherLlm",
            "model path=${modelStore.modelFile.absolutePath} " +
                "exists=${modelStore.modelFile.exists()} " +
                "size=${modelStore.modelFile.length()} " +
                "present=${modelStore.isModelPresent()}",
        )
    }

    @After
    fun tearDown() {
        client.close()
    }

    @Test
    fun reportsUnavailableWhenNoModelIsPresent() = runTest {
        assumeTrue("a model IS present; this test covers the absent case", !modelStore.isModelPresent())

        // Must be a typed, expected failure rather than a crash: no model is a
        // normal state before the user downloads one.
        assertThrows(LlmUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { client.generate("hello", 4) }
        }
    }

    @Test
    fun generatesTextFromAPrompt() = runTest {
        assumeTrue("no model downloaded", modelStore.isModelPresent())

        val output = client.generate("Reply with the single word: ready", 8)

        assertTrue("model produced no output", output.isNotBlank())
    }

    @Test
    fun classifiesAKnownNoteCorrectly() = runTest {
        assumeTrue("no model downloaded", modelStore.isModelPresent())

        // The real acceptance check for this milestone. "Produced some output"
        // would pass with a broken chat template producing fluent nonsense;
        // only a correct label proves the whole path works.
        val prompt = com.aether.domain.memory.MemoryClassifier.buildPrompt(
            "I keep skipping breakfast and want to stop",
        )
        val raw = client.generate(prompt, com.aether.domain.memory.MemoryClassifier.MAX_TOKENS)
        val type = com.aether.domain.memory.MemoryClassifier.parse(raw)

        assertTrue(
            "expected HEALTH or HABIT for a note about skipping meals, got $type (raw: '$raw')",
            type == com.aether.domain.memory.MemoryType.HEALTH ||
                type == com.aether.domain.memory.MemoryType.HABIT,
        )
    }

    @Test
    fun repeatedGenerationsAreIndependent() = runTest {
        assumeTrue("no model downloaded", modelStore.isModelPresent())

        val first = com.aether.domain.memory.MemoryClassifier.parse(
            client.generate(
                com.aether.domain.memory.MemoryClassifier.buildPrompt("I want to run a marathon"),
                com.aether.domain.memory.MemoryClassifier.MAX_TOKENS,
            ),
        )
        // Same prompt again: greedy sampling plus a cleared KV cache must give
        // the same answer. If the cache leaked between calls, one note's
        // content would bias the next note's label.
        val second = com.aether.domain.memory.MemoryClassifier.parse(
            client.generate(
                com.aether.domain.memory.MemoryClassifier.buildPrompt("I want to run a marathon"),
                com.aether.domain.memory.MemoryClassifier.MAX_TOKENS,
            ),
        )

        assertTrue("classification was not reproducible: $first then $second", first == second)
    }
}
