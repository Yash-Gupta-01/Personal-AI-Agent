package com.aether.domain.memory

/**
 * How a memory entered the system.
 *
 * Worth recording from the start: voice transcripts carry recognition errors
 * that typed text does not, so when the memory agent later judges how much to
 * trust a memory, provenance matters.
 */
enum class MemorySource {
    MANUAL_TEXT,
    MANUAL_VOICE,
}
