package com.aether.domain.memory

/**
 * Where a memory sits in its lifecycle.
 *
 * The full lifecycle in the design is
 * `New → Candidate → Verified → Updated → Archived → Forgotten`, but
 * `Candidate` and `Verified` only mean something once an LLM is proposing
 * memories on its own. Only the states with a reader today exist here; the
 * rest are added when the memory agent arrives.
 */
enum class MemoryStatus {
    ACTIVE,
    ARCHIVED,
}
