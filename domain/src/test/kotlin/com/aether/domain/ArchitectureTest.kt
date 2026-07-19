package com.aether.domain

import org.junit.Assert.fail
import org.junit.Test

/**
 * Enforces the single most important structural rule in Aether:
 * **the domain layer knows nothing about Android.**
 *
 * The whole Clean Architecture dependency rule rests on this. If Android types
 * leak into the domain, entities and use cases stop being testable without a
 * device, and infrastructure concerns start leaking upward into business logic.
 *
 * This is checked by inspecting the test classpath, which is a superset of the
 * main runtime classpath — so if Android is absent here, it is absent from the
 * production code too.
 *
 * The `:domain` build file applies no Android plugin, which is what actually
 * prevents this. The test exists so that accidentally adding one fails the
 * build loudly, rather than being noticed months later.
 */
class ArchitectureTest {

    @Test
    fun `domain classpath contains no Android classes`() {
        val androidClasses = listOf(
            "android.content.Context",
            "android.app.Activity",
            "android.os.Bundle",
            "androidx.room.RoomDatabase",
            "androidx.compose.runtime.Composable",
        )

        val leaked = androidClasses.filter { className ->
            try {
                Class.forName(className, false, javaClass.classLoader)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }

        if (leaked.isNotEmpty()) {
            fail(
                "The :domain module must not depend on Android, but these classes " +
                    "are on its classpath: $leaked\n" +
                    "Remove the Android dependency from domain/build.gradle.kts. " +
                    "Android-specific code belongs in :data, :llm, :tools or :app.",
            )
        }
    }

    @Test
    fun `kotlin stdlib and coroutines are available`() {
        // Sanity check: proves the test above fails for the right reason
        // (a genuinely absent Android) rather than a broken classloader.
        Class.forName("kotlin.Unit", false, javaClass.classLoader)
        Class.forName("kotlinx.coroutines.CoroutineScope", false, javaClass.classLoader)
    }
}
