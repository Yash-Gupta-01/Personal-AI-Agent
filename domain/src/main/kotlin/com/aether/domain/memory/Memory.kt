package com.aether.domain.memory

import java.time.Instant
import java.util.UUID

/**
 * A single structured thing Aether knows.
 *
 * Aether stores memories, not transcripts. A memory is meant to be *updated*
 * as understanding improves rather than appended to endlessly, which is why
 * [updatedAt] is distinct from [createdAt] from the very first version.
 *
 * Fields that have no reader yet — importance, confidence, version history,
 * relationships to other memories — are deliberately absent. Adding a column
 * later is a trivial Room migration; carrying an unused one is a guess about
 * the future that has to be maintained in the meantime.
 */
data class Memory(
    val id: String,
    val content: String,
    val type: MemoryType,
    val source: MemorySource,
    val status: MemoryStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(content.isNotBlank()) { "Memory content must not be blank" }
    }

    /** Returns a copy with new content, advancing [updatedAt]. */
    fun withContent(newContent: String, now: Instant = Instant.now()): Memory =
        copy(content = newContent.trim(), updatedAt = now)

    companion object {
        /**
         * Creates a newly captured memory.
         *
         * [now] and [id] are parameters rather than being read internally so
         * tests can pin them; production callers use the defaults.
         */
        fun capture(
            content: String,
            source: MemorySource,
            now: Instant = Instant.now(),
            id: String = UUID.randomUUID().toString(),
        ): Memory = Memory(
            id = id,
            content = content.trim(),
            type = MemoryType.UNCLASSIFIED,
            source = source,
            status = MemoryStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
    }
}
