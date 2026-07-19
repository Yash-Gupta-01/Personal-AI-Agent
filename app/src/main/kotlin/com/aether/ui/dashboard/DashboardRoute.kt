package com.aether.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.ui.capture.QuickCaptureSheet

object DashboardTestTags {
    const val CAPTURE_FAB = "dashboard_capture_fab"
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Aether", style = MaterialTheme.typography.titleLarge)
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
        )
    }

    if (isCapturing) {
        QuickCaptureSheet(
            onDismiss = { isCapturing = false },
            onCapture = viewModel::capture,
        )
    }
}
