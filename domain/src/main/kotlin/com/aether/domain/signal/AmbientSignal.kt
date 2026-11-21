package com.aether.domain.signal

import java.time.Instant
import java.time.LocalTime

/**
 * Passive OS signals captured from Android system broadcasts without user input.
 */
sealed interface AmbientSignal {
    val timestamp: Instant

    /** Device screen unlocked and user is present. */
    data class UserPresent(
        override val timestamp: Instant = Instant.now(),
        val localTime: LocalTime,
    ) : AmbientSignal

    /** An alarm was dismissed in the system or OEM Clock app. */
    data class AlarmDismissed(
        override val timestamp: Instant = Instant.now(),
        val label: String? = null,
    ) : AmbientSignal
}
