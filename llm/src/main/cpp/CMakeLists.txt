cmake_minimum_required(VERSION 3.22.1)
project(aether_llm CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Everything llama.cpp can build that Aether does not need. Each of these is
# minutes of compile time on a 4-core machine, and none of it ships in an app.
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)

# The NDK ships no libomp, so ggml's OpenMP path cannot link. ggml falls back to
# its own threadpool, which is what Android builds use anyway.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# GGML_NATIVE detects the *host* CPU, which is meaningless when cross-compiling
# from x86 Windows to arm64 Android, so it must be off.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# ...but with detection off, the ARM extensions must be named explicitly, or
# ggml silently falls back to plain NEON and loses the int8 matmul kernels that
# make CPU inference viable. That fallback is invisible except in
# llama_print_system_info(), which is why NativeLlamaTest asserts on it.
#
# armv8.2-a+dotprod+i8mm targets the Snapdragon 7+ Gen 3 (Cortex-X4, ARMv9.2);
# both extensions were confirmed present in /proc/cpuinfo on the device
# (asimddp, i8mm) before being enabled here. Lowering this below the device's
# actual capability costs several times the inference speed.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm" CACHE STRING "" FORCE)

add_subdirectory(llama.cpp)

add_library(aether_llm SHARED aether_llm.cpp)

target_link_libraries(aether_llm
    llama
    android
    log
)
