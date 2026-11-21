package com.aether.data.policy

import android.content.Context
import android.content.SharedPreferences
import com.aether.domain.policy.RestraintConfig
import com.aether.domain.policy.RestraintConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SharedPreferences-backed persistence for user restraint preferences.
 *
 * Stores user-configured quiet hours, master enable toggle, and daily interruption
 * budgets. Emits updates via Flow when preferences change.
 */
@Singleton
class SharedPreferencesRestraintConfigRepository @Inject constructor(
    @ApplicationContext context: Context,
) : RestraintConfigRepository {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun observeConfig(): Flow<RestraintConfig> = callbackFlow {
        // Emit current state initially
        trySend(readConfig())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(readConfig())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override suspend fun getConfig(): RestraintConfig = withContext(ioDispatcher) {
        readConfig()
    }

    override suspend fun updateConfig(config: RestraintConfig): Unit = withContext(ioDispatcher) {
        prefs.edit()
            .putInt(KEY_MAX_DAILY_INTERVENTIONS, config.maxDailyInterventions)
            .putBoolean(KEY_QUIET_HOURS_ENABLED, config.quietHoursEnabled)
            .putInt(KEY_QUIET_HOURS_START_HOUR, config.quietHoursStart.hour)
            .putInt(KEY_QUIET_HOURS_START_MINUTE, config.quietHoursStart.minute)
            .putInt(KEY_QUIET_HOURS_END_HOUR, config.quietHoursEnd.hour)
            .putInt(KEY_QUIET_HOURS_END_MINUTE, config.quietHoursEnd.minute)
            .putInt(KEY_MAX_CONSECUTIVE_DISMISSALS, config.maxConsecutiveDismissalsBeforeCooldown)
            .apply()
    }

    private fun readConfig(): RestraintConfig {
        val maxDaily = prefs.getInt(KEY_MAX_DAILY_INTERVENTIONS, 1)
        val quietEnabled = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, true)
        val startHour = prefs.getInt(KEY_QUIET_HOURS_START_HOUR, 22)
        val startMinute = prefs.getInt(KEY_QUIET_HOURS_START_MINUTE, 0)
        val endHour = prefs.getInt(KEY_QUIET_HOURS_END_HOUR, 7)
        val endMinute = prefs.getInt(KEY_QUIET_HOURS_END_MINUTE, 0)
        val maxDismissals = prefs.getInt(KEY_MAX_CONSECUTIVE_DISMISSALS, 3)

        return RestraintConfig(
            maxDailyInterventions = maxDaily,
            quietHoursEnabled = quietEnabled,
            quietHoursStart = LocalTime.of(startHour.coerceIn(0, 23), startMinute.coerceIn(0, 59)),
            quietHoursEnd = LocalTime.of(endHour.coerceIn(0, 23), endMinute.coerceIn(0, 59)),
            maxConsecutiveDismissalsBeforeCooldown = maxDismissals,
        )
    }

    companion object {
        private const val PREFS_NAME = "aether_restraint_preferences"
        private const val KEY_MAX_DAILY_INTERVENTIONS = "max_daily_interventions"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START_HOUR = "quiet_hours_start_hour"
        private const val KEY_QUIET_HOURS_START_MINUTE = "quiet_hours_start_minute"
        private const val KEY_QUIET_HOURS_END_HOUR = "quiet_hours_end_hour"
        private const val KEY_QUIET_HOURS_END_MINUTE = "quiet_hours_end_minute"
        private const val KEY_MAX_CONSECUTIVE_DISMISSALS = "max_consecutive_dismissals"
    }
}
