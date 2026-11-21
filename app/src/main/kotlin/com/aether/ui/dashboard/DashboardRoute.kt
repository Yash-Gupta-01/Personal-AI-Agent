package com.aether.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.ui.model.ModelSetupSheet
import com.aether.ui.settings.RestraintSettingsSheet
import kotlinx.coroutines.launch

object DashboardTestTags {
    const val CAPTURE_FAB = "dashboard_capture_fab"
    const val DELETE_MENU_ITEM = "dashboard_delete_menu_item"
    const val MODEL_SETUP_ACTION = "dashboard_model_setup_action"
}

/**
 * Stateful entry point for the assistant dashboard & timeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restraintConfig by viewModel.restraintConfig.collectAsStateWithLifecycle()
    val activeThreads by viewModel.activeThreads.collectAsStateWithLifecycle()
    val pendingActions by viewModel.pendingActions.collectAsStateWithLifecycle()
    val pendingFollowUps by viewModel.pendingFollowUps.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    var isSettingUpModel by remember { mutableStateOf(false) }
    var isConfiguringRestraint by remember { mutableStateOf(false) }
    val restraintSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Aether", style = MaterialTheme.typography.titleLarge)
                        val primaryThread = activeThreads.firstOrNull()
                        if (primaryThread != null) {
                            val subtitle = buildString {
                                append(primaryThread.title)
                                if (primaryThread.consecutiveSuccesses > 0) append(" • Streak ${primaryThread.consecutiveSuccesses}")
                                primaryThread.knownBlockers.firstOrNull()?.let { append(" • Blocked: $it") }
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { isConfiguringRestraint = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Restraint Boundaries & Quiet Hours")
                    }
                    TextButton(
                        onClick = { isSettingUpModel = true },
                        modifier = Modifier.testTag(DashboardTestTags.MODEL_SETUP_ACTION),
                    ) { Text("AI") }
                },
            )
        },
        bottomBar = {
            TimelineBottomBar(
                onCapture = viewModel::capture,
            )
        },
    ) { innerPadding ->
        DashboardContent(
            state = state,
            pendingAction = pendingActions.firstOrNull(),
            pendingFollowUp = pendingFollowUps.firstOrNull(),
            onExecuteAction = { actionId -> viewModel.executeAction(actionId, context) },
            onDismissAction = { actionId -> viewModel.dismissAction(actionId, context) },
            onRespondFollowUp = { actionId, reply -> viewModel.respondToFollowUp(actionId, reply, context) },
            contentPadding = innerPadding,
            onDelete = { memory ->
                viewModel.delete(memory)
                scope.launch {
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

    if (isSettingUpModel) {
        ModelSetupSheet(onDismiss = { isSettingUpModel = false })
    }

    if (isConfiguringRestraint) {
        RestraintSettingsSheet(
            config = restraintConfig,
            activeThreads = activeThreads,
            sheetState = restraintSheetState,
            onDismissRequest = { isConfiguringRestraint = false },
            onUpdateConfig = viewModel::updateRestraintConfig,
            onClearThreadCooldown = viewModel::clearThreadCooldown,
            onRemoveBlocker = viewModel::removeThreadBlocker,
        )
    }
}
