package com.aether.llm

/**
 * The only Kotlin object permitted to touch llama.cpp's native symbols.
 *
 * Everything above this is written against the domain's `LlmClient` port, so
 * swapping llama.cpp for another runtime means replacing this file and its DI
 * binding — not touching assistant logic.
 *
 * Handles are raw native pointers. [freeModel] must be called exactly once for
 * every non-zero handle from [loadModel]; calling into a freed handle is a
 * SIGSEGV, not an exception. [LlamaCppClient] owns that lifecycle so no other
 * code has to think about it.
 */
internal object NativeLlama {

    /**
     * True once the native library is present and loadable.
     *
     * Checked rather than assumed: a missing or mismatched .so throws at class
     * initialisation, and a hard crash on app start would be a poor way to
     * discover the model simply is not usable on this device.
     */
    val isAvailable: Boolean = try {
        System.loadLibrary("aether_llm")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    /** llama.cpp's report of the CPU features it compiled against. */
    external fun systemInfo(): String

    /** Resolves a trivial native symbol; proves JNI wiring end to end. */
    external fun isLoaded(): Boolean

    /** Returns an opaque handle, or 0 if the model could not be loaded. */
    external fun loadModel(modelPath: String, contextSize: Int, threads: Int): Long

    /** Releases a handle. Safe to call with 0. */
    external fun freeModel(handle: Long)

    /**
     * Formats [userMessage] with the model's own chat template from its GGUF
     * metadata, or returns null if the model carries none.
     */
    external fun applyChatTemplate(handle: Long, userMessage: String): String?

    /** Generates up to [maxTokens], or null on failure. */
    external fun generate(handle: Long, prompt: String, maxTokens: Int): String?
}
