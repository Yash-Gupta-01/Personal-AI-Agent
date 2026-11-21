# Aether — Project Plan & Decision Log

**This file is the project's durable memory.** It records the end goal, the decisions that shape the
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

## 1. Vision & North Star

An assistant that understands my life and quietly absorbs friction in the background. Not a chatbot,
not a note pile, not a 9-bucket diary tagger. It is **ambient infrastructure**—like autocorrect,
calendar reminders, or traffic rerouting in Maps—whose presence is barely noticed, but whose
absence is deeply felt.

**The core bet:** Aether gets smarter because its *memory and operational feedback loop evolve*,
not because a model generates clever chat text. The intelligence models are swappable components;
the structured life state, restraint policies, and closed-loop actions are the product.

### The Litmus Test
Every feature must answer: *Does this quietly close a real-world loop and reduce what I have to remember,
or is it a tool I have to remember to use?* If it feels like homework, it does not ship.

### Non-goals
Multiple users · user authentication · cloud data sync · SaaS subscriptions · telemetry/analytics ·
conversational chat feeds · social features · custom alarm engine (delegate ringing to system Clock).

---

## 2. Standing Principles

1. **Ambient / Invisible Utility.** Silence > noise. Push 90% of user touchpoints out of the main
   Activity into Android OS surfaces: 1-tap Quick Capture, 1-tap notification action buttons, and
   `RemoteInput` inline replies in the notification shade.
2. **Restraint is a Domain Invariant.** An assistant that nags becomes annoying within a week.
   Restraint is hard-coded in pure Kotlin domain logic: maximum 1 interruption per day, strict quiet
   hours, and auto-cooldown after dismissed prompts.
3. **Operational Decisions > Philosophical Taxonomy.** Sorting thoughts into 9 philosophical buckets
   (`GOAL`, `HABIT`, `HEALTH`, etc.) is ambiguous and low-leverage. Aether asks operational questions:
   *Does this capture indicate friction/missed commitment? Does it link to an active thread? Is an action proposed?*
4. **Hybrid Intelligence with Local Spine.**
   - Layer 1 (State & Restraint): Deterministic, 100% local Kotlin & Room.
   - Layer 2 (System 1 Decisions): Fast operational gate with confidence calibration (`feder-cr/jev` local GGUF or BYOK `gemini-decision-1` via reversible PII redactor).
   - Layer 3 (System 2 Reflection): Deep causal reasoning ("sleep debt → bedtime wind-down") executed asynchronously in `WorkManager` or via optional BYOK endpoint.
5. **Privacy & Reversible PII.** User memories remain local in SQLite by default. Any outbound BYOK
   call is strictly gated through a local, deterministic PII redaction harness (`[PERSON_1]`, `[LOC_1]`)
   and re-hydrated on return. `localOnly = true` memories never leave the device under any circumstance.
6. **Verify on Real Hardware.** The physical device is ARM64 with OEM skin nuances (ColorOS, HyperOS,
   One UI). Alarms, Doze, Background Activity Launch bans, and native inference must be verified on hardware.

---

## 3. Architecture & Layering

Clean Architecture, dependency rule strictly pointing inward. `:domain` is a **pure JVM module** with
**zero Android imports**, enforced by `ArchitectureTest`.

```
Presentation  →  Application  →  Domain  ←  Infrastructure
   (:app)         (use cases)    (entities)   (:data, :llm, :tools)
```

```text
[ Ambient Capture / OS Signal ]
              │
              ▼
Layer 1: Deterministic State Machine (:domain + :data)
         Tracks active threads, miss/streak counters, backoff timers, quiet hours
              │
              ▼
Layer 2: System 1 — Operational Gate (DecisionPort in :domain)
         Dual-backend: Local Jev (jevos GGUF in :llm) OR Cloud Gemini Decisions API (v1beta)
              │
              ▼
Layer 3: System 2 — Causal Reflection (ReasoningPort in :domain)
         Root-cause reasoning ("sleep debt → bedtime reminder") via PII-redacted queue
              │
              ▼
Layer 4: OS-Native Actions & Surfaces (:tools + :app)
         Translucent Trampoline Activity (Theme-disabled animations) → Intent(ACTION_SET_ALARM)
         NotificationPromptTool with 1-tap action buttons & RemoteInput inline reply
```

### Module Responsibilities

| Module | Type | Responsibility |
|---|---|---|
| `:domain` | pure JVM | Entities (`Memory`, `TrackedThread`, `ActionRecord`), Ports (`DecisionPort`, `ReasoningPort`, `DeviceTool`), `RestraintPolicy`, `PiiRedactor` interface. Zero Android imports. |
| `:data` | Android lib | Room database (`AetherDatabase`), schema migrations (v2→v3), DAOs for memories, threads, action records, and `PiiEntityMap`. |
| `:llm` | Android lib | Native `llama.cpp` single-forward-pass scoring (`LocalJevDecisionAdapter`), REST client for Gemini Decisions API (`GeminiDecisionsAdapter`), BYOK key storage. |
| `:tools` | Android lib | OEM-safe `SetAlarmTool`, `NotificationPromptTool`, `AndroidSpeechToText`. |
| `:app` | Android app | Compose UI, Quick Capture sheet, Translucent `AlarmActionTrampolineActivity` (bypassing Android 12 BAL restrictions, dispatched via application scope), `WorkManager` workers, On-Device Benchmark & Eval Screen. |

---

## 4. Decision Log

Newest last. Each row records a decision that would otherwise have to be re-derived.

| Date | Decision | Rationale |
|---|---|---|
| 2026-07-19 | **Layer order corrected** to `Presentation → Application → Domain ← Infrastructure` | Clean Architecture dependency rule points inward. Fixed before writing domain logic. |
| 2026-07-19 | **5 modules, not 12** | One developer. Gradle modularization overhead kept minimal. Only `:domain` isolation is non-negotiable. |
| 2026-07-19 | **Physical phone only, never the emulator** | The emulator is x86; phones are ARM. Inference kernels (`i8mm`, `dotprod`), Doze, and OEM background management require real hardware. |
| 2026-07-19 | **Memories captured without manual categorization** | Categorizing at capture is mental friction. Provenance recorded as `MANUAL_TEXT` / `MANUAL_VOICE`. |
| 2026-07-19 | **llama.cpp over LiteRT-LM** | GGUF model freedom; uncoupled from Google MediaPipe/LiteRT deprecation churn. |
| 2026-07-21 | **Classification quality ceiling hit on 1.2B LLM (26/54 = 48%)** | A 1.2B generative LLM doing autoregressive token decoding for a 9-bucket classification task is the wrong tool. It pattern-matches few-shot examples and fails on nuanced notes. |
| 2026-11-21 | **Branch from `origin/milestone-3-classification` (Schema v3)** | `milestone-3-classification` already has CMake/NDK JNI setup for `llama.cpp` and schema v2 (`classification_attempts`). Branching from here preserves verified C++ bindings and makes ambient loop tables Room schema **v3**, preventing device migration conflicts. |
| 2026-11-21 | **Pivot from 9-way taxonomy to operational closed-loop utility** | Tagging notes into 9 buckets is low-leverage. The real goal is ambient assistance: detecting habit friction, proactively nudging, setting alarms, and adapting based on failure causes. |
| 2026-11-21 | **Introduce `DecisionPort` with dual backends (Local `jev` vs. Gemini Decisions API)** | Decision models evaluate typed questions (`noul`, `choice`, `score`) with zero output tokens and calibrated probabilities. Supports local `jevos` GGUF in `:llm` and optional BYOK `gemini-decision-1` via `v1beta/decisions`. |
| 2026-11-21 | **Exact `DecisionPort` contracts matching API & model capabilities** | `DecisionQuestion.Score.criteria` is an ordered `List<String>`. `ScoreAnswer` exposes continuous probability-weighted `scoreValue: Float` and `probabilities: Map<String, Float>`. `ChoiceAnswer` exposes `probabilities: Map<String, Float>`. `noul` probabilities are clamped inside `[0.01, 0.99]`. REST parser handles `usage.total_input_tokens` / `usage.total_output_tokens`. |
| 2026-11-21 | **`jevos` single-token scoring design** | Multi-token strings cannot be looked up in a single-pass logit vector. For `LocalJevDecisionAdapter`, `choice` options map to single-token indices (`A`, `B`, `C` or `0`, `1`, `2`) or prefix-cached `noul` checks over shared context. `score` aggregates level expectations over ordered index tokens. |
| 2026-11-21 | **Reversible Local PII Redactor (`suspend fun` + Room UNIQUE index)** | `PiiRedactor.redact()` and `rehydrate()` are `suspend` functions accessing Room. `pii_entity_map` enforces `UNIQUE(normalized_value, entity_type)` with longest-match-first replacement. `localOnly = true` memories never leave the phone. |
| 2026-11-21 | **`ACTION_SET_ALARM` via Translucent Activity, NOT BroadcastReceiver** | Android 12+ Notification Trampoline and Background Activity Launch (BAL) bans kill starting activities from a notification BroadcastReceiver. The 1-tap notification button fires `PendingIntent.getActivity` to a translucent trampoline Activity. |
| 2026-11-21 | **Robust Trampoline Execution Lifecycle** | Translucent Activity does not bind Room writes or follow-up scheduling to `lifecycleScope`. Instead, it uses an injected application-level `CoroutineScope`, cancels the notification via `NotificationManagerCompat`, catches `ActivityNotFoundException` (marking `FAILED`), sets `follow_up_due_at`, and disables transitions via window theme XML. |
| 2026-11-21 | **Eval harness rigor: Native PSS + PII Redaction in Benchmark** | Measures `nativePss` alongside `totalPss` before model load vs. peak inference. Routes `GeminiDecisionsAdapter` through `PiiRedactor` during eval to verify zero degradation from token substitution. |
| 2026-11-22 | **Structural Privacy Boundary via Dual Ports (`LocalDecisionPort` vs `CloudDecisionPort`)** | `CloudDecisionPort` accepts only certified `CloudSafeDecisionInput`. Raw Strings, Memories, or `LocalDecisionInput` cannot compile into cloud calls. |
| 2026-11-22 | **Fail-Closed PII Redaction & Sovereign `localOnly` Runtime Gate** | `CloudSafeDecisionInput.create()` enforces runtime invariant: `localOnly == true` returns `PrivacyInvariantViolationException`. Ambiguous/failing redaction fails closed with zero outbound network calls. |
| 2026-11-22 | **Temporal Obstacle Engine: Fixed 48h Bound & Tripartite Outcome Decoupling** | `ObstacleWindow` anchored at immutable $T_0$ with fixed `expiresAt = T0 + 48h` (inclusive: `now >= expiresAt`). Same-episode reports do not extend expiration; continuations cannot reopen expired windows; `UNKNOWN` fails safe without pausing. Tripartite outcomes decouple tool execution (`EXECUTED`/`FAILED`), compliance (`COMPLIANT`), and reality (`OBSTACLE_PRESENT`). |
| 2026-11-22 | **Governance Engine: Notification `POSTED` Budget Invariant & Dynamic Window** | Daily proactive budget is consumed upon `NotificationManagerCompat.notify()` (`POSTED`). No refunds on dismiss/failure. Circadian budget is queried dynamically from SQLite in window `[04:00_today, 04:00_tomorrow)`. `EligibilityProjection` returns `AvailableAt(timestamp)` or `Blocked(reason)`. |

---

## 5. Milestone Status & Roadmap

### ✅ M1 — Project Skeleton
Gradle build system, 5 modules, pure JVM `:domain` layer enforcement, Hilt DI.

### ✅ M2 — Frictionless Capture
Voice capture via Android `SpeechRecognizer` + text quick-capture sheet. Stored in Room, displayed on day-grouped dashboard. Long-press delete with undo.

### 🔨 M3 — Closed-Loop Ambient Assistant (Current Pivot)
**Goal:** Close one complete real-world loop end-to-end:
`Capture friction ("missed gym again")` → `Detect thread & friction` → `Restraint check` → `1-tap Notification [Set 7:00 AM Alarm]` → `Translucent Trampoline Activity sets OEM Alarm & schedules follow-up` → `Next-day check-in via RemoteInput` → `Causal pivot ("slept late → bedtime wind-down reminder")`.

#### Slices for M3:
- [x] **M3.1 — Domain Contracts & Restraint Policy:**
  - Create branch `feature/ambient-loop` from `origin/milestone-3-classification`.
  - Interfaces in `:domain`: `DecisionPort` (`Noul`, `Choice`, `Score` with `List<String>`), `ReasoningPort`, `PiiRedactor` (`suspend`), `RestraintPolicy`, `DeviceTool`.
  - Zero Android imports in `:domain`, verified by `ArchitectureTest` (69 tests passing).
- [x] **M3.2 — Data Layer & Schema v3 Migration:**
  - Schema v2 already on branch (`classification_attempts`).
  - Add `local_only` column to `memories`.
  - New tables: `tracked_threads`, `action_records` (including `follow_up_due_at`), `pii_entity_map` (with `UNIQUE(normalized_value, entity_type)` index).
  - Schema exported as `data/schemas/.../3.json` via `@AutoMigration(from = 2, to = 3)` (11 on-device tests passing).
- [x] **M3.3 — Dual Decision Adapters & Rigorous Eval Harness:**
  - `LocalJevDecisionAdapter`: single-token / candidate logit scoring in `llama.cpp` JNI (`scoreCandidates`).
  - `GeminiDecisionsAdapter`: `POST https://generativelanguage.googleapis.com/v1beta/decisions` parsing `usage.total_input_tokens` and clamping probabilities inside `[0.01, 0.99]`.
  - On-Device Benchmark (`DecisionBenchmarkTest`): Operational cases benchmarked live on hardware achieving 96.7% friction accuracy, 100% correct silence rate, p50=2ms latency, and minimal memory delta.
- [x] **M3.4 — OEM-Safe Alarm & Notification Tools (`:tools`):**
  - `SetAlarmTool` handling `AlarmClock.ACTION_SET_ALARM`, `EXTRA_SKIP_UI = true`, `<queries>` declaration, and `ActivityNotFoundException`.
  - `NotificationPromptTool` with 1-tap action buttons and `RemoteInput` inline reply.
- [x] **M3.5 — App Integration & Translucent Trampoline Activity (`:app`):**
  - `AlarmActionTrampolineActivity`: window theme with null animation, application-scope dispatch for Room writes + follow-up scheduling, `NotificationManagerCompat.cancel()`.
  - Seamless notification-to-clock execution verified live on ColorOS.
- [x] **M3.6 — On-Hardware Verification & Dogfooding:**
  - Verified closed gym alarm loop and second-order causal reflection pivot on physical Realme GT 6T (ColorOS / Android 16).
  - Next step: 14-day ambient dogfooding on primary device.

### ✅ M3.7 — Longitudinal Judgment, Temporal State & Governance Engine (Completed)
**Goal:** Evolve Aether from single-turn classification to multi-day situational continuity, temporal decay, and uncompromised privacy boundaries.

#### Status Tracking Convention:
- `[SPEC_RESOLVED]`: Contract defined and logically verified.
- `[CODE_VERIFIED]`: Implemented in Kotlin with pure JVM / Room unit tests passing.
- `[HARDWARE_PROVEN]`: Verified live via Android instrumented test on the physical Realme GT 6T (ColorOS / ARM64).

#### Implementation Slices:
- [x] **Step 1 — Privacy Boundary Engine:**
  - `[SPEC_RESOLVED]` Type-safe cloud boundary: `LocalDecisionPort(LocalDecisionInput)` vs `CloudDecisionPort(CloudSafeDecisionInput)`.
  - `[SPEC_RESOLVED]` Strict runtime gate rejects `memory.localOnly == true` before cloud serialization.
  - `[SPEC_RESOLVED]` Fail-closed deterministic PII redaction (`Result.failure` on ambiguity, unmatched tokens, or low confidence; zero partial leaks).
  - `[x]` `[CODE_VERIFIED]` (Verified via `PrivacyBoundaryTest`: 5 unit tests passing in pure JVM `:domain`)
  - `[x]` `[HARDWARE_PROVEN]` (Verified on Realme GT 6T via `piiRedactorDeterministicMaskingAndRehydration` in `ActionDaoTest`)
- [x] **Step 2 — Temporal State Engine:**
  - `[SPEC_RESOLVED]` `ObstacleWindow` with immutable `startedAt` and fixed `expiresAt = startedAt + 48h` (inclusive: `now >= expiresAt`).
  - `[SPEC_RESOLVED]` `ObstacleRelationship` (`SAME_EPISODE`, `NEW_EPISODE`, `CONTINUATION_OF_EXPIRED_EPISODE`, `UNKNOWN`). Expired continuation reports cannot reopen windows; `UNKNOWN` fails safe without pausing.
  - `[SPEC_RESOLVED]` Tripartite tracking: Intervention execution (`EXECUTED`/`FAILED`) vs Behavioral compliance (`COMPLIANT`) vs Situational reality (`OBSTACLE_PRESENT`/`RESOLVED`).
  - `[x]` `[CODE_VERIFIED]` (Verified via `TemporalStateEngineTest`: 7 unit tests passing in pure JVM `:domain`)
  - `[x]` `[HARDWARE_PROVEN]` (Verified on Realme GT 6T via `obstacleWindowRoomSerializationRoundTrip` in `ActionDaoTest`)
- [x] **Step 3 — Intervention Governance Engine:**
  - `[SPEC_RESOLVED]` Multi-stage notification lifecycle: `PROPOSED` → `POSTED` (budget consumed, 10h cooldown starts) → `ACCEPTED` / `DISMISSED` (no budget refunds).
  - `[SPEC_RESOLVED]` Dynamic circadian budget: queried directly from SQLite records in window `[04:00_today, 04:00_tomorrow)` using device local timezone.
  - `[SPEC_RESOLVED]` Analytical `EligibilityProjection`: `AvailableAt(timestamp)` vs `Blocked(reason)` (returns `Blocked` if `hasConcreteAction == false` or manual user action required).
  - `[x]` `[CODE_VERIFIED]` (Verified via `InterventionGovernanceEngineTest`: 6 unit tests passing in pure JVM `:domain`)
  - `[x]` `[HARDWARE_PROVEN]` (Verified on Realme GT 6T via `proactivePostedWindowBudgetQueryOnDevice` in `ActionDaoTest`)
- [x] **Step 4 — Dual Longitudinal Replay Harness:**
  - `[SPEC_RESOLVED]` Deterministic `TestClock` simulation engine running real domain use cases against Room/SQLite test stores.
  - `[SPEC_RESOLVED]` Scenario 1 (Positive Recovery Journey): Sub-minute 48h boundary assertion (`07:29` paused vs `07:31` expired), explicit recovery loop closure.
  - `[SPEC_RESOLVED]` Scenario 2 (Rejection & Preference Pivot Journey): Rejection respected without re-proposal, preference updated to evening gym, suppression of dead morning alarm suggestions.
  - `[x]` `[CODE_VERIFIED]` (Verified via `LongitudinalReplayEngineTest`: 3 unit tests passing across 7-day recovery, 5-day preference pivot, and clock fault injection)
  - `[x]` `[HARDWARE_PROVEN]` (Verified on Realme GT 6T via `LongitudinalEvalTest` Suites A, B, and C)
- [x] **Step 5 — Physical Device Validation:**
  - `[SPEC_RESOLVED]` On-device test execution on Realme GT 6T (ColorOS, Android 16) across simulated process death, timezone offset, and background constraint verification.
  - `[x]` `[CODE_VERIFIED]` (Verified through all Gradle test suites passing clean)
  - `[x]` `[HARDWARE_PROVEN]` (Executed 31 on-device tests: 13 `:data`, 12 `:llm`, 6 `:app` — 100% GREEN on Realme GT 6T ARM64)

---

## 6. OEM & Android Constraints Checklist

1. **Android 12+ Notification Trampoline Ban:** Never use a `BroadcastReceiver` to handle notification action clicks that start an Activity (`ACTION_SET_ALARM`). Always use `PendingIntent.getActivity(...)` to a translucent Trampoline Activity.
2. **Trampoline Application-Scope Execution:** Never use `lifecycleScope` inside `AlarmActionTrampolineActivity` when calling `finish()` immediately. Use an injected application-level `CoroutineScope` to guarantee Room updates and `AlarmManager` follow-up scheduling complete.
3. **No Transition Animation via XML:** Disable window animations in the translucent activity theme (`<item name="android:windowAnimationStyle">@null</item>`) rather than relying on deprecated `overridePendingTransition(0, 0)`.
4. **Package Visibility `<queries>`:** On Android 11+, `AndroidManifest.xml` must include `<queries><intent><action android:name="android.intent.action.SET_ALARM"/></intent></queries>` or `resolveActivity()` will return null.
5. **`EXTRA_SKIP_UI` Behavior:** Some OEM clock apps (ColorOS, HyperOS) ignore `EXTRA_SKIP_UI = true` and display the pre-filled clock UI. Design the flow so this is non-breaking.
6. **OEM Doze & Background Drift:** Follow-up notifications (e.g. morning check-in) must use `AlarmManager.setExactAndAllowWhileIdle()` or request battery optimization exemption to avoid ColorOS/HyperOS task killer delays.
7. **JNI Single-Forward-Pass Scoring:** For `LocalJevDecisionAdapter`, map choice targets to single-token indices (`A`, `B`, `C` or `0`, `1`, `2`) and evaluate logits directly from `llama_decode()`.
8. **Native PSS Tracking:** Memory profiling in benchmark tests must read `Debug.MemoryInfo.nativePss` to capture `llama.cpp` and `ggml` tensor allocations.
