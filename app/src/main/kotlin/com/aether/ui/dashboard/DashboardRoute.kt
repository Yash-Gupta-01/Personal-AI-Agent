package com.aether.ui.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Aether", style = MaterialTheme.typography.titleLarge)
                },
            )
        },
    ) { innerPadding ->
        DashboardContent(
            state = state,
            contentPadding = innerPadding,
        )
    }
}
