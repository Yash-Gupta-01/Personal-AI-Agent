package com.aether.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.ui.capture.QuickCaptureSheet
import com.aether.ui.model.ModelSetupSheet

object DashboardTestTags {
    const val CAPTURE_FAB = "dashboard_capture_fab"
    const val DELETE_MENU_ITEM = "dashboard_delete_menu_item"
    const val MODEL_SETUP_ACTION = "dashboard_model_setup_action"
}

/**
 * Stateful entry point for the dashboard.
 *
 * Kept apart from [DashboardContent] so the content stays previewable and
 * testable with plain data, with no Hilt graph or database involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle, not collectAsState: collection stops when
    // the app is backgrounded rather than holding a database subscription open.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isCapturing by remember { mutableStateOf(false) }
    var isSettingUpModel by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Aether", style = MaterialTheme.typography.titleLarge)
                },
                actions = {
                    TextButton(
                        onClick = { isSettingUpModel = true },
                        modifier = Modifier.testTag(DashboardTestTags.MODEL_SETUP_ACTION),
                    ) { Text("AI") }
                },
            )
        },
        floatingActionButton = {
            // Bottom-right: the one primary action on the screen, in the
            // easiest place to reach one-handed.
            FloatingActionButton(
                onClick = { isCapturing = true },
                modifier = Modifier.testTag(DashboardTestTags.CAPTURE_FAB),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Capture a thought")
            }
        },
    ) { innerPadding ->
        DashboardContent(
            state = state,
            contentPadding = innerPadding,
            onDelete = { memory ->
                viewModel.delete(memory)
                scope.launch {
                    // Undo restores the exact memory, id and timestamps
                    // included, so it returns to its original place in the
                    // timeline rather than reappearing as if newly captured.
                    val result = snackbarHostState.showSnackbar(
                        message = "Memory deleted",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restore(memory)
                    }
                }
            },
        )
    }

    if (isCapturing) {
        QuickCaptureSheet(
            onDismiss = { isCapturing = false },
            onCapture = viewModel::capture,
        )
    }

    if (isSettingUpModel) {
        ModelSetupSheet(onDismiss = { isSettingUpModel = false })
    }
}
