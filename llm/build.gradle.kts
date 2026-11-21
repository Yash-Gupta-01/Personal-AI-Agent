import java.util.Properties

// Infrastructure layer: on-device inference via llama.cpp.
//
// Implements the LlmClient port declared in :domain, so the rest of the app
// never knows which model or runtime is behind it. Nothing outside this module
// may reference llama.cpp — that is what keeps a backend swap to one commit.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.aether.llm"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject GEMINI_API_KEY from .env, local.properties, or system environment
        val envFile = rootProject.file(".env")
        val localPropsFile = rootProject.file("local.properties")
        var apiKey = System.getenv("GEMINI_API_KEY") ?: ""
        if (apiKey.isEmpty() && envFile.exists()) {
            envFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("GEMINI_API_KEY=")) {
                    apiKey = trimmed.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
                }
            }
        }
        if (apiKey.isEmpty() && localPropsFile.exists()) {
            val props = Properties().apply { load(localPropsFile.inputStream()) }
            apiKey = props.getProperty("gemini.api.key") ?: props.getProperty("GEMINI_API_KEY") ?: ""
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"$apiKey\"")

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

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
