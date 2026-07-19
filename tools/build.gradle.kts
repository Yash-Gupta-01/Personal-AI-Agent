// Infrastructure layer: the tool registry and the Android-API-backed tools
// (alarms, reminders, notifications, clipboard).
//
// The assistant never touches Android APIs directly — every capability is a
// tool with a declared schema. Keeping the schemas JSON-Schema-shaped means an
// MCP adapter can be layered on later without reworking the registry.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aether.tools"
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
