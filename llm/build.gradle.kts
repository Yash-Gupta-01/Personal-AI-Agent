// Infrastructure layer: on-device inference via llama.cpp.
//
// Implements the LLM port declared in :domain, so the rest of the app never
// knows which model or runtime is behind it.
//
// The NDK/CMake native build and the JNI bindings arrive in M2. The target
// device (Snapdragon 7+ Gen 3, ARMv9 with i8mm and dotprod) means this will
// be built arm64-v8a only — there is no reason to carry other ABIs.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aether.llm"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":domain"))
}
