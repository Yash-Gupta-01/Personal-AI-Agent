package com.aether.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryDay
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.groupByDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    /**
     * Stores a captured thought.
     *
     * Blank input is ignored rather than surfaced as an error: an empty
     * capture is a mis-tap, and scolding the user for one helps nobody.
     */
    fun capture(content: String, source: MemorySource = MemorySource.MANUAL_TEXT) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repository.save(Memory.capture(content, source))
        }
    }

    /**
     * Deletes a memory, returning it so the caller can offer an undo.
     *
     * The memory is handed back rather than soft-deleted with a flag: undo is
     * simply saving it again, which keeps the storage layer free of a
     * "deleted but not really" state that every future query would have to
     * remember to filter out.
     */
    fun delete(memory: Memory) {
        viewModelScope.launch { repository.delete(memory.id) }
    }

    /** Restores a deleted memory, unchanged including its original id and timestamps. */
    fun restore(memory: Memory) {
        viewModelScope.launch { repository.save(memory) }
    }

    private companion object {
        // Keeps the flow alive briefly across configuration changes so a
        // rotation does not re-query the database.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
