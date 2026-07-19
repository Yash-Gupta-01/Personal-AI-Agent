package com.aether.llm

/**
 * The only Kotlin object permitted to touch llama.cpp's native symbols.
 *
 * Everything above this is written against the domain's `LlmClient` port, so
 * swapping llama.cpp for another runtime means replacing this file and its DI
 * binding — not touching assistant logic.
 *
 * Currently a probe only. Model loading and generation land in the next commit;
 * this exists to prove the NDK toolchain, CMake and llama.cpp actually link and
 * resolve on arm64 before any real bindings depend on that.
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
}
