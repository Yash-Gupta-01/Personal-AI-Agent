# Aether

**Your second brain.**

A single-user, offline-first personal AI assistant for Android. Aether is not a
chat app that happens to store notes. It builds an evolving understanding of
your goals, habits, projects, relationships and routines, and uses that
understanding to reduce how much you have to remember.

The guiding bet: **Aether gets smarter because its memory evolves, not because
the model changes.** The LLM is just another replaceable component.

Every feature must answer one question: *does this reduce the amount the user
has to remember?*

## Status

**Milestone 2 — capture.** Aether is usable: capture a thought by voice or
text, and it is stored as a structured memory and shown on the dashboard,
grouped by day. Long-press a memory to delete it, with undo.

There is no LLM yet. Every memory is captured as `UNCLASSIFIED`; classifying
them is M3's job.

## The capture loop

```
Voice ─┐
       ├─► Transcript ─► Memory (UNCLASSIFIED) ─► Room ─► Dashboard
Text ──┘
```

**You are never asked to categorise a thought.** Choosing a type, a title or
tags at capture time is precisely the mental work Aether exists to remove, so
capture asks for one thing — the thought — and classification is deferred to
the memory agent.

Provenance is recorded: text that came from speech is stored as
`MANUAL_VOICE`, because transcripts carry recognition errors typed text does
not, and later reasoning needs to know how much to trust the wording.

## Architecture

Clean Architecture. The dependency rule points inward, and `:domain` is the
centre:

```
Presentation  →  Application  →  Domain  ←  Infrastructure
   (:app)         (use cases)    (entities)   (:data, :llm, :tools)
```

`:domain` depends on nothing. Infrastructure implements ports declared in the
domain, so persistence, inference and Android APIs are all replaceable without
touching business logic.

| Module | Type | Responsibility |
|---|---|---|
| `:domain` | pure JVM | `Memory`, the `MemoryRepository` port, day grouping. Planner, journal and habit logic will live here as packages. |
| `:data` | Android library | Room database, `RoomMemoryRepository`. Schema JSON exported to `data/schemas` so migrations are reviewable as diffs. |
| `:llm` | Android library | On-device inference via llama.cpp. Empty until M3. |
| `:tools` | Android library | `SpeechToText`; later the tool registry and Android-API tools (alarms, reminders, notifications). |
| `:app` | Android app | Compose UI, ViewModels, DI wiring. |

**`:domain` applies no Android plugin.** This is the project's most important
structural rule: it makes `import android.*` a compile error, and lets domain
tests run in milliseconds without a device. `ArchitectureTest` fails the build
if an Android class ever reaches the domain classpath.

### Principles

- **Offline-first.** Your data is local and the app is fully usable without a
  network. Inference is on-device.
- **Everything is a tool.** The assistant never calls Android APIs directly.
  Capabilities are tools with declared schemas.
- **Memory is structured, not a transcript.** Raw conversations are not kept
  forever; typed memories are updated rather than endlessly appended.
- **Build only what the current milestone needs.**

## Requirements

- **Android Studio** (2026.1.2 or newer) — also supplies the Android SDK and a
  Java 21 runtime
- **A physical Android device** with USB debugging enabled

The emulator is deliberately unsupported. It is x86 while phones are ARM, so
llama.cpp performance measured on it is meaningless; alarms, Doze and speech
recognition also only behave correctly on real hardware.

### Java version

The Gradle daemon is pinned to Java 21 in `gradle/gradle-daemon-jvm.properties`,
because the Android Gradle Plugin does not support newer JDKs. If Gradle cannot
find a Java 21 installation, point it at Android Studio's bundled runtime in
`~/.gradle/gradle.properties` (machine-local, never committed):

```properties
org.gradle.java.installations.paths=C\:\\Program Files\\Android\\Android Studio\\jbr
```

`local.properties` must contain your SDK path. Android Studio writes it
automatically on first open; it is gitignored.

## Build and run

```bash
adb devices                      # phone must show 'device', not 'offline'

./gradlew :domain:test           # pure JVM tests — fast, no device needed
./gradlew build                  # compile all modules, run unit tests and lint
./gradlew installDebug           # install on the connected phone
./gradlew connectedAndroidTest   # instrumented tests on real hardware
```

Two traps worth knowing:

- **`build` does not compile instrumented tests.** A broken `androidTest`
  source compiles fine under `build` and only fails at
  `connectedAndroidTest`. Run `assembleAndroidTest` if you want the check
  without a device.
- **`connectedAndroidTest` uninstalls the app afterwards**, wiping the
  database. If you want the app left on the phone, run `installDebug` last.

## Versions

All versions live in `gradle/libs.versions.toml` — never hardcode one in a
module build file. Two constraints are easy to trip over:

- **Kotlin must match KSP.** KSP2 tracks Kotlin releases exactly, so bumping
  Kotlin past the newest KSP release breaks Hilt and Room.
- **AGP 9 has built-in Kotlin support.** Applying
  `org.jetbrains.kotlin.android` to an Android module is rejected outright.

## Conventions

- Commits stay small (~300–500 LOC) and each one compiles, passes tests and is
  independently useful.
- Deliberate shortcuts are marked with a `ponytail:` comment naming the ceiling
  and the upgrade path, so they get revisited instead of forgotten.
