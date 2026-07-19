package com.aether.ui.model

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.llm.DownloadState

object ModelSetupTestTags {
    const val DOWNLOAD_BUTTON = "model_download_button"
    const val PROGRESS = "model_download_progress"
    const val STATUS = "model_status"
}

/**
 * Downloads the on-device model.
 *
 * Shown as a sheet rather than a mandatory first-run gate: Aether is fully
 * usable for capture without a model, and blocking the app behind a 700MB
 * download before it has proved itself would be a poor first impression.
 * Classification simply starts working once the model arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSetupSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "On-device intelligence",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Aether classifies your thoughts using a model that runs entirely on " +
                    "this phone. Nothing is sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                state.isModelReady -> Text(
                    text = "${state.modelName} is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(ModelSetupTestTags.STATUS),
                )

                else -> ModelDownloadControls(state = state, onDownload = viewModel::startDownload)
            }
        }
    }
}

@Composable
private fun ModelDownloadControls(
    state: ModelSetupUiState,
    onDownload: () -> Unit,
) {
    when (val download = state.download) {
        is DownloadState.Downloading -> {
            val downloadedMb = download.bytesDownloaded / (1024 * 1024)
            val totalMb = download.totalBytes / (1024 * 1024)
            // Determinate progress, not a spinner: a 700MB transfer behind an
            // indeterminate indicator is indistinguishable from a hang, and the
            // natural response is to kill the app mid-download.
            LinearProgressIndicator(
                progress = { download.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ModelSetupTestTags.PROGRESS),
            )
            Text(
                text = "$downloadedMb MB of $totalMb MB",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(ModelSetupTestTags.STATUS),
            )
        }

        is DownloadState.Finalising -> Text(
            text = "Moving the model into private storage…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(ModelSetupTestTags.STATUS),
        )

        is DownloadState.Pending -> Text(
            text = "Waiting for Wi-Fi…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(ModelSetupTestTags.STATUS),
        )

        is DownloadState.Failed -> {
            Text(
                text = download.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(ModelSetupTestTags.STATUS),
            )
            Button(
                onClick = onDownload,
                modifier = Modifier.testTag(ModelSetupTestTags.DOWNLOAD_BUTTON),
            ) { Text("Try again") }
        }

        else -> {
            Text(
                text = "${state.modelName} · about ${state.approxMegabytes} MB · Wi-Fi only",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ModelSetupTestTags.STATUS),
            )
            Button(
                onClick = onDownload,
                modifier = Modifier.testTag(ModelSetupTestTags.DOWNLOAD_BUTTON),
            ) { Text("Download model") }
        }
    }
}
