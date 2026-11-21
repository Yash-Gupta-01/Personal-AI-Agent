package com.aether.llm

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the native layer actually links and loads on the device.
 *
 * There is no model involved yet. This isolates the riskiest part of the
 * milestone — NDK toolchain, CMake, llama.cpp, JNI symbol resolution on
 * arm64 — so that when inference code arrives, a failure is unambiguously in
 * the new code rather than in the build.
 */
@RunWith(AndroidJUnit4::class)
class NativeLlamaTest {

    @Test
    fun nativeLibraryLoads() {
        assertTrue("libaether_llm.so failed to load", NativeLlama.isAvailable)
    }

    @Test
    fun jniSymbolsResolve() {
        // A declared `external fun` that was never compiled throws
        // UnsatisfiedLinkError only when called, not when the library loads.
        assertTrue(NativeLlama.isLoaded())
    }

    @Test
    fun cpuReportsTheArmMatmulExtensions() {
        val info = NativeLlama.systemInfo()

        // Logged, not just asserted: which kernels are active is the single
        // best predictor of inference speed on this device, and it is the first
        // thing worth reading when performance looks wrong.
        Log.i("AetherLlm", "llama.cpp system info: $info")

        assertTrue("llama.cpp reported no system info", info.isNotBlank())

        // Both are required, not "any of". An earlier version of this test
        // accepted NEON alone and passed while dotprod and i8mm were silently
        // disabled — the precise regression it was written to catch. A test
        // that cannot fail for its own reason is worse than no test.
        //
        // The device has both (confirmed in /proc/cpuinfo: asimddp, i8mm), so
        // anything less means the build lost them, not that the CPU lacks them.
        assertTrue(
            "llama.cpp is not using ARM dotprod — check GGML_CPU_ARM_ARCH: $info",
            info.contains("DOTPROD = 1"),
        )
        assertTrue(
            "llama.cpp is not using ARM i8mm — check GGML_CPU_ARM_ARCH: $info",
            info.contains("MATMUL_INT8 = 1"),
        )
    }
}
