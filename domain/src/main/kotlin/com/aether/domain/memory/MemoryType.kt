package com.aether.domain.memory

/**
 * What kind of thing a memory is about.
 *
 * The user is never asked to choose one. Categorising a thought at the moment
 * of capture is exactly the mental work Aether exists to remove, so everything
 * is captured as [UNCLASSIFIED] and classified later by the memory agent.
 */
enum class MemoryType {
    GOAL,
    PREFERENCE,
    HABIT,
    RELATIONSHIP,
    CAREER,
    PROJECT,
    HEALTH,
    KNOWLEDGE,
    REFLECTION,

    /** Captured but not yet classified. Every memory starts here. */
    UNCLASSIFIED,
}
