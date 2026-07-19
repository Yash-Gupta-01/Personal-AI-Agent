// Infrastructure layer: Room database and repository implementations.
// Depends on :domain and implements the ports declared there. Nothing in
// :domain may ever depend back on this module.
//
// Room and KSP are added in M2, alongside the first entity — not before.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aether.data"
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
