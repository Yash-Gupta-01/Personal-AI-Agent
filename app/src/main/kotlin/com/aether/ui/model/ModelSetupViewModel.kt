package com.aether.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.llm.DownloadState
import com.aether.llm.ModelCatalog
import com.aether.llm.ModelDownloader
import com.aether.llm.ModelStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelSetupUiState(
    val modelName: String = ModelCatalog.DISPLAY_NAME,
    val approxMegabytes: Long = ModelCatalog.APPROX_MEGABYTES,
    val isModelReady: Boolean = false,
    val download: DownloadState = DownloadState.Idle,
) {
    val isDownloading: Boolean
        get() = download is DownloadState.Downloading ||
            download is DownloadState.Pending ||
            download is DownloadState.Finalising
}

@HiltViewModel
class ModelSetupViewModel @Inject constructor(
    private val modelStore: ModelStore,
    private val downloader: ModelDownloader,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ModelSetupUiState(isModelReady = modelStore.isModelPresent()),
    )
    val uiState: StateFlow<ModelSetupUiState> = _uiState.asStateFlow()

    fun startDownload() {
        if (_uiState.value.isDownloading) return
        viewModelScope.launch {
            downloader.download().collect { state ->
                _uiState.value = _uiState.value.copy(
                    download = state,
                    // Re-checked from disk rather than inferred from
                    // DownloadState.Completed, so the UI only claims readiness
                    // when a correctly sized file actually exists.
                    isModelReady = modelStore.isModelPresent(),
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isModelReady = modelStore.isModelPresent())
    }
}
