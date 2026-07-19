// :domain is a PURE JVM module. It deliberately does not apply any Android
// plugin, so `import android.*` is a compile error rather than a code-review
// note. Its tests run in milliseconds without a device or emulator.
//
// This module must depend on nothing but Kotlin and coroutines. Adding an
// Android dependency here breaks the layer rule the whole project rests on.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
