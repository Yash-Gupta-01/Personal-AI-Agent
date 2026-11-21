package com.aether.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.domain.action.ProcessFrictionNudge
import com.aether.domain.memory.ClassifyMemories
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryDay
import com.aether.domain.memory.MemoryRepository
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.groupByDay
import com.aether.tools.notification.NotificationPromptTool
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    private val classifyMemories: ClassifyMemories,
    private val processFrictionNudge: ProcessFrictionNudge? = null,
    private val notificationPromptTool: NotificationPromptTool? = null,
) : ViewModel() {

    // Guards against several passes running at once. They would not corrupt
    // anything — inference is serialised inside the client — but each capture
    // would queue another full batch behind the running one, so a burst of
    // captures would do the same work many times over.
    private val classifying = Mutex()

    init {
        // Clears whatever accumulated while the app was closed. Classification
        // has no UI of its own: the type simply appears on the card, because
        // saving a classified memory makes Room re-emit the dashboard's flow.
        classifyInBackground()
    }

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
            classifyInBackground()

            // Evaluate ambient routine friction & prompt proactive notification
            processFrictionNudge?.let { nudge ->
                val proposed = nudge.invoke(content)
                if (proposed != null && notificationPromptTool != null) {
                    val hour = proposed.toolParams["hour"]?.toIntOrNull() ?: 7
                    val minute = proposed.toolParams["minute"]?.toIntOrNull() ?: 0
                    val label = proposed.toolParams["label"] ?: "Gym"
                    val title = proposed.toolParams["nudge_title"] ?: "Morning Gym Routine"
                    val body = proposed.toolParams["nudge_body"]
                        ?: "I noticed you missed the gym today. Would a %02d:%02d alarm help get you there tomorrow?".format(hour, minute)
                    val confirmButtonText = proposed.toolParams["confirm_button_text"]
                        ?: "Set %02d:%02d Alarm".format(hour, minute)

                    notificationPromptTool.postNudge(
                        notificationId = 1001,
                        actionId = proposed.id,
                        title = title,
                        body = body,
                        confirmButtonText = confirmButtonText,
                        hour = hour,
                        minute = minute,
                        label = label,
                    )
                }
            }
        }
    }

    /**
     * Classifies whatever is pending, if a pass is not already running.
     *
     * Scoped to the ViewModel, so leaving the app mid-inference abandons the
     * pass. That is safe rather than lossy: the queue lives in SQLite, so the
     * next launch picks up exactly where this left off.
     *
     * ponytail: no WorkManager. Classification needs the app open, which is
     * fine while the only consumer of a type is a card the user is looking at.
     * Add one when something acts on memories while the app is closed — a
     * reminder, a proactive nudge — not before.
     */
    private fun classifyInBackground() {
        if (!classifying.tryLock()) return
        viewModelScope.launch {
            try {
                classifyMemories.invoke()
            } finally {
                classifying.unlock()
            }
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
