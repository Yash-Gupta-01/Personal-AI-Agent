// Infrastructure layer: on-device inference via llama.cpp.
//
// Implements the LlmClient port declared in :domain, so the rest of the app
// never knows which model or runtime is behind it. Nothing outside this module
// may reference llama.cpp — that is what keeps a backend swap to one commit.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aether.llm"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64-v8a only. Every other ABI is dead weight in the APK and
            // minutes of native compile time per build, and no target device
            // needs them. The emulator is x86 and deliberately unsupported.
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                // AGP compiles native code as Debug for the debug variant,
                // which builds ggml's kernels unoptimised and makes inference
                // several times slower. Since debug is the variant actually
                // installed and measured on the phone, that would produce
                // misleading benchmarks and invite blaming the model.
                //
                // RelWithDebInfo rather than Release: optimised, but keeps the
                // symbols that make a native crash in the JNI layer readable.
                arguments += "-DCMAKE_BUILD_TYPE=RelWithDebInfo"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":domain"))

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
