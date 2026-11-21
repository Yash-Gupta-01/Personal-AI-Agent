package com.aether.domain.action

/**
 * State in the deterministic proactive action lifecycle:
 *
 * PROPOSED ─► POSTED ─► APPROVED ─► EXECUTED ─► OBSERVED ─► EVALUATED
 *     │           │          │           │           │
 *     ├───────────┼──────────┴───────────┼───────────┤
 *     ▼           ▼                      ▼           ▼
 *  SUPPRESSED  DISMISSED              FAILED      EXPIRED
 */
enum class ActionStatus {
    /** Proposed by reasoning agent, awaiting restraint evaluation. */
    PROPOSED,

    /** Posted to notification shade by NotificationManager. Consumes circadian budget. */
    POSTED,

    /** User confirmed the suggestion (or auto-approved by policy). */
    APPROVED,

    /** Dispatched to device tool (e.g. Alarm intent launched via trampoline Activity). */
    EXECUTED,

    /** Follow-up check-in delivered to notification shade. */
    OBSERVED,

    /** Follow-up answered and root cause evaluated. Terminal state. */
    EVALUATED,

    /** User dismissed the notification prompt or tapped 'Not now'. Terminal state. */
    DISMISSED,

    /** Action expired before execution or follow-up response. Terminal state. */
    EXPIRED,

    /** Execution failed (e.g. no clock app found on device). Terminal state. */
    FAILED,

    /** Suppressed by restraint policy or quiet hours before reaching notification shade. */
    SUPPRESSED,

    /** Cancelled or superseded before user interaction. */
    CANCELLED,
}
