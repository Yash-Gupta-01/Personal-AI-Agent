package com.aether.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.domain.memory.MemoryDay
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.memory.groupByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State for the dashboard.
 *
 * There is no `isLoading` flag. Reads come from local SQLite and arrive in
 * roughly a frame, so a loading state would flash and read as a glitch rather
 * than as progress. There is no offline state either: Aether is offline-first,
 * so being offline is normal operation, not degradation.
 */
data class DashboardUiState(
    val days: List<MemoryDay> = emptyList(),
) {
    val isEmpty: Boolean get() = days.isEmpty()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: MemoryRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        repository.observeActive()
            .map { memories -> DashboardUiState(days = memories.groupByDay()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DashboardUiState(),
            )

    private companion object {
        // Keeps the flow alive briefly across configuration changes so a
        // rotation does not re-query the database.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
