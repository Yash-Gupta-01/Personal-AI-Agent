# Aether — Project Plan & Decision Log

**This file is the project's memory.** It records the end goal, the decisions that shape the
codebase and *why* they were made, what is done, and what is deliberately deferred.

It exists because reasoning gets lost. Code shows *what*; commit messages show *why* for one
change; this file shows why the project is the shape it is. When a decision looks wrong later,
the rationale here is what tells you whether the reason still holds.

**It records decisions and status only.** It must not duplicate what the code or `README.md`
already says, or it becomes a second, wrong source of truth.

> **Update protocol** — update this file *in the same commit as the change*, whenever:
> a decision is taken or reversed · a commit in the current milestone completes · a milestone
> merges · something is deferred (always with the trigger that brings it back).

---

## 1. Vision

An assistant that understands my life. Not a chat log, not a note pile, not a reminder app.
It builds an evolving understanding of my goals, habits, projects, relationships, routines,
career, mistakes, strengths and preferences — and uses it to help me.

**The bet:** Aether gets smarter because its *memory* evolves, not because the model changes.
The LLM is a replaceable component. The memory is the product.

### Non-goals

Multiple users · authentication · cloud sync · payments · subscriptions · analytics ·
social features · server backend.

Not "later" — these are not being built. Design accordingly.

---

## 2. Standing principles

1. **Reduce cognitive load.** Every feature answers: *does this reduce what I have to
   remember?* If not, it does not ship.
2. **Never make the user categorise.** Sorting a thought into a bucket is the work Aether
   exists to remove. Capture asks for the thought and nothing else.
3. **Offline-first.** Data is local, the app is fully usable with no network. Offline is
   normal operation, not a degraded state worth announcing.
4. **The LLM is a component.** Nothing outside `:llm` may import an inference runtime.
   Swapping backends must cost one commit, not a redesign.
5. **Retrieval beats inference.** The bottleneck will be *which* memories reach the prompt,
   not how fast tokens generate. Cutting 200 retrieved memories to the 10 most relevant buys
   more than any runtime swap. Invest there before optimising the model.
6. **Build only what the current milestone needs.** A field, method or abstraction with no
   reader is a guess that has to be maintained. Adding it later is nearly always cheap.
7. **Verify on hardware.** A green build is not evidence the app works. Every milestone is
   driven through the real UI on a real phone before it is called done.

---

## 3. Architecture

Clean Architecture, dependency rule pointing inward:

```
Presentation  →  Application  →  Domain  ←  Infrastructure
   (:app)         (use cases)    (entities)   (:data, :llm, :tools)
```

`:domain` depends on nothing and is a **pure JVM module**, so `import android.*` inside it is
a compile error, not a review comment. `ArchitectureTest` fails the build if an Android class
ever reaches its classpath. Domain tests run in milliseconds with no device.

| Module | Type | Holds |
|---|---|---|
| `:domain` | pure JVM | Entities, ports, use cases |
| `:data` | Android lib | Room, repository implementations |
| `:llm` | Android lib | llama.cpp, JNI, model management |
| `:tools` | Android lib | Speech; later the tool registry and Android-API tools |
| `:app` | Android app | Compose, ViewModels, DI wiring |

---

## 4. Decision log

Newest last. Each row is a decision that would otherwise have to be re-derived.

| Date | Decision | Rationale |
|---|---|---|
| 2026-07-19 | **Layer order corrected** to `Presentation → Application → Domain ← Infrastructure` | The original spec had Domain depending on Infrastructure — the dependency rule inverted. Fixed before any code was written against it. |
| 2026-07-19 | **5 modules, not 12** | One developer. Each Gradle module costs a build file and build time. Only the pure-JVM `:domain` boundary pays for itself immediately. Engines live as packages until one proves it needs isolation. |
| 2026-07-19 | **No vector-search abstraction** | Brute-force cosine over a realistic memory count (~20k) is a few ms in plain Kotlin. An interface with one implementation is a guess. |
| 2026-07-19 | **Local-only LLM** | Privacy-absolute, user's explicit choice. Confirmed viable: the GT 6T exposes `i8mm`, `asimddp`, `asimdhp` — the ARM matmul extensions llama.cpp needs. |
| 2026-07-19 | **Physical phone only, never the emulator** | The emulator is x86, phones are ARM; inference benchmarks on it measure a different code path entirely. Alarms, Doze and speech are also only real on hardware. |
| 2026-07-19 | **Memories captured `UNCLASSIFIED`** | Classification is the model's job, not the user's. This removed a UI component rather than adding one. |
| 2026-07-19 | **Provenance recorded from day one** (`MANUAL_TEXT` / `MANUAL_VOICE`) | Voice transcripts carry recognition errors typed text does not. Later reasoning needs to know how much to trust the wording. |
| 2026-07-19 | **No onboarding, no loading skeleton, no offline banner** | The core action is obvious, so an empty state teaches better than a carousel. Local reads arrive within a frame, so a spinner would flash and read as a glitch. Offline is normal for an offline-first app. |
| 2026-07-19 | **Delete via long-press, not swipe** | A swipe needs a visible non-gesture alternative anyway to stay accessible, so it is strictly more code for the same outcome. |
| 2026-07-19 | **Undo re-saves the original object; no soft-delete flag** | A "deleted but not really" state means every future query must remember to filter it, and forgetting once silently resurrects memories. |
| 2026-07-19 | **llama.cpp (upstream, pinned) over LiteRT-LM** | GGUF freedom: any HuggingFace model, any quantization. LiteRT-LM cannot load arbitrary GGUF. Also, Google deprecated the MediaPipe LLM API in favour of LiteRT-LM — that churn is a real risk for a multi-year project's most critical component. |
| 2026-07-19 | **TurboQuant deferred, not used** | It compresses the *KV cache*, which dominates only at 128K–1M context. Aether's real workload is 2–8K, occasionally 16K — it optimises the wrong axis. Also still unmerged upstream (PR #21089). Existing GGUFs run unchanged if it lands, so waiting costs nothing. |
| 2026-07-19 | **`LlmClient` exposes `generate()` only** | `embeddings()` arrives with semantic search in M4; `tokenize()`/`healthCheck()` when a caller exists. An unimplemented method is a burden every future backend must satisfy, including the LiteRT-LM fallback. |
| 2026-07-19 | **All commits are made by the user, not the assistant** | Authorship and commit granularity stay under human control. The assistant writes and verifies code and leaves the working tree staged-ready; the user reviews and commits. |
| 2026-07-19 | **Repo-local git identity** (`Yash Gupta <guptayash112003@gmail.com>`) | This is a personal project; the global identity is a work account. Set at repo scope only, so other repositories are unaffected. Commits before this date carry the work email. |
| 2026-07-19 | **Never ask a small model for JSON** | 1.5B models emit malformed JSON often enough to be a real failure source. Classification asks for one word from a closed set and validates against the enum, falling back to `UNCLASSIFIED`. Robust by construction, not by prompt-begging. |

---

## 5. Milestone status

### ✅ M1 — Project skeleton (`89501e8`)

Build system, 5-module layout, launchable Compose app with Hilt. Gradle 9.6.1, AGP 9.3.0,
Kotlin 2.3.10, JDK 21. Verified on device.

### ✅ M2 — Capture (`a271dc8`)

Capture by voice or text → stored as a structured memory → dashboard grouped by day.
Long-press to delete with undo. 33 tests green; whole loop driven through the real UI on
hardware, with the voice capture read back out of SQLite to confirm `source=MANUAL_VOICE`.

Risk closed: on-device speech works on ColorOS — it needed a `<queries>` entry for
`android.speech.RecognitionService`, without which Android 11+ package-visibility filtering
hides every recogniser and `isRecognitionAvailable()` returns false on a device that has one.

### 🔨 M3 — Classification (in progress)

**Goal:** a local model classifies each captured memory in the background, with no user
action. The type appears on the card. First milestone where memory evolves on its own.

- [x] 0 · `docs: add plan.md as the project's durable memory`
- [ ] 1 · `feat(domain): memory classification prompt and parser`
- [ ] 2 · `build(llm): llama.cpp native build via NDK`
- [ ] 3 · `feat(llm): JNI bindings and llama.cpp inference client`
- [ ] 4 · `feat(llm): model download with progress` ← **confirm exact GGUF repo with user first**
- [ ] 5 · `feat(data): unclassified query and schema v2 migration`
- [ ] 6 · `feat(app): background classification with type on cards` ← the payoff
- [ ] 7 · `docs: update README and plan.md`

Commits 1–3 and 5 produce nothing visible. That is the honest shape of this milestone.

**Model:** Qwen 1.5B–3B class, `Q4_K_M`. Exact repo to be chosen before commit 4.

**Schema v2** adds `classification_attempts`, so a memory the model repeatedly fails on stops
being retried instead of draining battery forever.

**Escape hatch:** if NDK/JNI stalls, the `LlmClient` port means switching to LiteRT-LM costs
one commit. This is deliberate and should be used rather than sunk-cost-ed.

### M4+ — sketched, not committed

Semantic search (embeddings + brute-force cosine) · memory updating rather than appending ·
the dashboard's remaining sections (priorities, streaks, insights) · tool registry ·
reminders and alarms.

Per principle 5, M4 should be **retrieval**, not a bigger model.

---

## 6. Deferred, with triggers

| Deferred | Bring it back when |
|---|---|
| Vector search library | Brute-force cosine is *measurably* too slow. Probably never. |
| Separate agent classes | One prompt measurably fails at two different jobs. |
| MCP server | Exposing Aether's memory to desktop clients is actually wanted. It is a thin adapter over the tool registry, not a rewrite. |
| Memory version history & relationship graph | A feature reads them. Adding a table is a trivial Room migration. |
| `importance` / `confidence` fields | Something ranks or filters on them. |
| Lifecycle states `CANDIDATE` / `VERIFIED` | The model proposes memories unprompted, rather than classifying ones the user wrote. |
| TurboQuant | It merges upstream **and** context regularly exceeds ~32K. Both conditions, not either. |
| Ktor | A networked tool exists. The model download uses Android's `DownloadManager`. |
| In-app model picker | More than one model is genuinely in use. |

---

## 7. Environment & gotchas

Hard-won; each of these cost real time.

- **adb over USB does not work on this machine.** Windows enumerates the ADB interface
  correctly (WinUSB driver, `GUID_DEVINTERFACE_ANDROID_USB` registered and Enabled) but adb
  finds zero devices. Use **wireless debugging**: `adb pair <ip>:<pairing-port> <code>` with
  the pairing dialog open, then `adb connect <ip>:<connect-port>`. `adb mdns services` finds
  the connect port. Ports rotate every session. Set `ANDROID_SERIAL` — two transports appear
  for the one phone.
- **JDK:** the machine's default is JDK 26, which AGP rejects. The daemon is pinned to Java 21
  via `gradle/gradle-daemon-jvm.properties`; Android Studio's bundled JBR supplies it.
- **AGP 9 has built-in Kotlin support** and *rejects* the `org.jetbrains.kotlin.android`
  plugin on Android modules.
- **Kotlin must match KSP.** KSP2 tracks Kotlin releases exactly; a newer Kotlin than the
  latest KSP breaks Hilt and Room.
- **`./gradlew build` does not compile instrumented tests.** Use
  `build assembleAndroidTest`.
- **`connectedAndroidTest` uninstalls the app afterwards**, wiping the database. Run
  `installDebug` *last* if you want the app left on the phone.
- **Room uses WAL** — inspecting the database means reading `aether.db-wal`, not just
  `aether.db`.
- Never run the emulator: 4 cores and 16GB are shared with Android Studio.
