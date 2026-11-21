package com.aether.domain.thread

/**
 * State of an ongoing life thread or tracked routine.
 */
enum class ThreadStatus {
    /** Actively monitored for routines, streaks, and friction. */
    ACTIVE,

    /** Temporarily paused by user preference or cooldown. */
    PAUSED,

    /** Closed out or completed; no proactive nudges will be triggered. */
    RESOLVED,
}
