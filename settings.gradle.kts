pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Aether"

// Layer rule: :domain depends on nothing. Everything else may depend on :domain.
//
// Planned modules, each added here by the commit that creates it:
//   :domain  pure JVM     — entities, ports, use cases
//   :data    Android lib  — Room, repository implementations
//   :llm     Android lib  — local llama.cpp inference
//   :tools   Android lib  — tool registry, Android API tools
//   :app     Android app  — Compose UI, ViewModels, DI wiring
