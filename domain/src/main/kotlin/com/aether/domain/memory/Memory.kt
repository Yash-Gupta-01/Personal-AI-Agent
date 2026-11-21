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
 * [localOnly] enforces the strict privacy boundary: memories flagged as
 * local-only are strictly barred from being passed to any outbound BYOK
 * model call, even under local PII pseudonymization.
 */
data class Memory(
    val id: String,
    val content: String,
    val type: MemoryType,
    val source: MemorySource,
    val status: MemoryStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val localOnly: Boolean = false,
) {
    init {
        require(content.isNotBlank()) { "Memory content must not be blank" }
    }

    /** Returns a copy with new content, advancing [updatedAt]. */
    fun withContent(newContent: String, now: Instant = Instant.now()): Memory =
        copy(content = newContent.trim(), updatedAt = now)

    /**
     * Returns a copy classified as [newType], advancing [updatedAt].
     *
     * This is the first way a memory changes without the user touching it —
     * the point at which memory starts evolving on its own.
     */
    fun withType(newType: MemoryType, now: Instant = Instant.now()): Memory =
        copy(type = newType, updatedAt = now)

    /** Returns a copy with updated privacy boundary [localOnly], advancing [updatedAt]. */
    fun withLocalOnly(localOnly: Boolean, now: Instant = Instant.now()): Memory =
        copy(localOnly = localOnly, updatedAt = now)

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
            localOnly: Boolean = false,
        ): Memory = Memory(
            id = id,
            content = content.trim(),
            type = MemoryType.UNCLASSIFIED,
            source = source,
            status = MemoryStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            localOnly = localOnly,
        )
    }
}
