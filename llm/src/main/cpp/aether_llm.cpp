// JNI surface for llama.cpp.
//
// This commit deliberately exposes only a probe. Getting the NDK toolchain,
// CMake and llama.cpp to link on arm64 is the risky part of this milestone;
// proving that in isolation means a failure here is a build problem, not a
// build problem tangled up with new inference code.
//
// Model loading and generation arrive in the next commit.

#include <jni.h>
#include <string>

#include "llama.h"

extern "C" {

/**
 * Returns llama.cpp's own report of the CPU features it compiled against.
 *
 * Worth having permanently: it is the ground truth for whether the fast ARM
 * matmul kernels (dotprod, i8mm) are actually active on this device. Inference
 * being unexpectedly slow almost always shows up here first.
 */
JNIEXPORT jstring JNICALL
Java_com_aether_llm_NativeLlama_systemInfo(JNIEnv *env, jobject /* this */) {
    const char *info = llama_print_system_info();
    return env->NewStringUTF(info == nullptr ? "" : info);
}

/** Proves the library loaded and JNI symbol resolution works. */
JNIEXPORT jboolean JNICALL
Java_com_aether_llm_NativeLlama_isLoaded(JNIEnv * /* env */, jobject /* this */) {
    return JNI_TRUE;
}

} // extern "C"
