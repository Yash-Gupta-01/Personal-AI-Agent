package com.aether.domain.policy

import kotlinx.coroutines.flow.Flow

/**
 * Storage port for user-configured restraint boundaries.
 *
 * Allows the user to configure quiet hours, daily interruption budgets,
 * and dismissal thresholds, persisting across app restarts.
 */
interface RestraintConfigRepository {

    /** Emits updates whenever the user modifies restraint preferences. */
    fun observeConfig(): Flow<RestraintConfig>

    /** Returns the current active configuration. */
    suspend fun getConfig(): RestraintConfig

    /** Updates and persists the configuration. */
    suspend fun updateConfig(config: RestraintConfig)
}
