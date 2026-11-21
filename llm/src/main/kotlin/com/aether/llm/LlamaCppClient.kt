package com.aether.llm

import android.util.Log
import com.aether.domain.llm.LlmClient
import com.aether.domain.llm.LlmUnavailableException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * llama.cpp implementation of the domain's [LlmClient] port.
 *
 * Owns the native model handle for the process. The model is loaded lazily on
 * first use and kept: loading a quantised model costs seconds and hundreds of
 * MB of mapping, so reloading per classification would dominate the work.
 *
 * All native calls are serialised behind a [Mutex]. llama.cpp's context is not
 * thread-safe, and two coroutines decoding into the same context corrupts the
 * KV cache — which surfaces as garbled output or a SIGSEGV, neither of which
 * points at concurrency. Classification is a background batch, so serialising
 * costs nothing real.
 */
class LlamaCppClient(
    private val modelStore: ModelStore,
    private val dispatcher: CoroutineDispatcher,
    private val contextSize: Int = DEFAULT_CONTEXT_SIZE,
    private val threads: Int = DEFAULT_THREADS,
) : LlmClient {

    private val mutex = Mutex()
    private var handle: Long = NO_HANDLE

    override suspend fun generate(prompt: String, maxTokens: Int): String =
        withContext(dispatcher) {
            mutex.withLock {
                val model = ensureLoaded()

                // Use the model's own chat template when it has one. Feeding a
                // model the wrong format produces fluent nonsense that looks
                // exactly like the model being incapable, so this is never
                // hardcoded per model family.
                val formatted = NativeLlama.applyChatTemplate(model, prompt) ?: prompt

                NativeLlama.generate(model, formatted, maxTokens)
                    ?: throw LlmUnavailableException("Generation failed")
            }
        }

    /** Frees the native model. Safe to call more than once. */
    fun close() {
        if (handle != NO_HANDLE) {
            NativeLlama.freeModel(handle)
            handle = NO_HANDLE
        }
    }

    private fun ensureLoaded(): Long {
        if (handle != NO_HANDLE) return handle

        if (!NativeLlama.isAvailable) {
            throw LlmUnavailableException("Native inference library is not available")
        }
        if (!modelStore.isModelPresent()) {
            throw LlmUnavailableException("No model has been downloaded yet")
        }

        val loaded = NativeLlama.loadModel(
            modelPath = modelStore.modelFile.absolutePath,
            contextSize = contextSize,
            threads = threads,
        )
        if (loaded == NO_HANDLE) {
            throw LlmUnavailableException("Model failed to load")
        }

        Log.i(TAG, "model loaded (ctx=$contextSize, threads=$threads)")
        handle = loaded
        return loaded
    }

    private companion object {
        const val TAG = "AetherLlm"
        const val NO_HANDLE = 0L

        // Classification prompts are a few hundred tokens. A larger context
        // would allocate a bigger KV cache for no benefit; memory matters more
        // than headroom on a phone.
        const val DEFAULT_CONTEXT_SIZE = 2048

        // The target SoC is 1+3+4: matching the four performance cores keeps
        // the little cores free and avoids the scheduler bouncing work between
        // core types, which costs more than the extra threads gain.
        const val DEFAULT_THREADS = 4
    }
}
