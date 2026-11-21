package com.aether.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aether.domain.action.ActionRecord
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryDay
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.MemoryType
import com.aether.domain.timeline.TimelineDay
import com.aether.domain.timeline.TimelineItem
import com.aether.ui.theme.AetherTheme
import java.time.Instant
import java.time.LocalDate

/**
 * The home screen: what Aether currently knows and active assistant routines, newest first.
 *
 * Integrates user thoughts, assistant interventions, execution receipts,
 * follow-up check-ins, and causal reflections into a unified timeline stream.
 */
@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    pendingAction: ActionRecord? = null,
    pendingFollowUp: ActionRecord? = null,
    onExecuteAction: (String) -> Unit = {},
    onDismissAction: (String) -> Unit = {},
    onRespondFollowUp: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onDelete: (Memory) -> Unit = {},
) {
    if (state.isEmpty && pendingAction == null && pendingFollowUp == null) {
        EmptyState(modifier = modifier.padding(contentPadding))
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // If unified timeline days are present, render the complete assistant stream
        if (state.timelineDays.isNotEmpty()) {
            state.timelineDays.forEach { day ->
                item(key = "timeline-header-${day.date}") {
                    DayHeader(date = day.date)
                }
                items(items = day.items, key = { it.id }) { item ->
                    when (item) {
                        is TimelineItem.Thought -> {
                            MemoryCard(memory = item.memory, onDelete = { onDelete(item.memory) })
                        }
                        is TimelineItem.Intervention -> {
                            ActiveInterventionCard(
                                action = item.action,
                                onExecute = onExecuteAction,
                                onDismiss = onDismissAction,
                            )
                        }
                        is TimelineItem.FollowUpCheckIn -> {
                            ActiveFollowUpCard(
                                action = item.action,
                                onRespond = onRespondFollowUp,
                            )
                        }
                        is TimelineItem.ExecutedReceipt -> {
                            ExecutedReceiptCard(action = item.action)
                        }
                        is TimelineItem.EvaluatedReflection -> {
                            EvaluatedReflectionCard(reflection = item)
                        }
                        is TimelineItem.DismissedReceipt -> {
                            DismissedReceiptCard(action = item.action)
                        }
                    }
                }
            }
        } else {
            // Legacy / fallback rendering for tests or preview
            if (pendingAction != null) {
                item(key = "pending-action-${pendingAction.id}") {
                    ActiveInterventionCard(
                        action = pendingAction,
                        onExecute = onExecuteAction,
                        onDismiss = onDismissAction,
                    )
                }
            }

            if (pendingFollowUp != null) {
                item(key = "pending-followup-${pendingFollowUp.id}") {
                    ActiveFollowUpCard(
                        action = pendingFollowUp,
                        onRespond = onRespondFollowUp,
                    )
                }
            }

            state.days.forEach { day ->
                item(key = "header-${day.date}") {
                    DayHeader(date = day.date)
                }
                items(items = day.memories, key = { it.id }) { memory ->
                    MemoryCard(memory = memory, onDelete = { onDelete(memory) })
                }
            }
        }
    }
}

@Composable
private fun ActiveInterventionCard(
    action: ActionRecord,
    onExecute: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = action.toolParams["nudge_title"] ?: "Routine Suggestion"
    val body = action.toolParams["nudge_body"] ?: "Would an alarm help tomorrow?"
    val confirmText = action.toolParams["confirm_button_text"] ?: "Confirm"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onDismiss(action.id) }) {
                    Text("Dismiss")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onExecute(action.id) }) {
                    Text(confirmText)
                }
            }
        }
    }
}

@Composable
private fun ActiveFollowUpCard(
    action: ActionRecord,
    onRespond: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Morning Check-In",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Did your alarm get you to the gym this morning?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { onRespond(action.id, "Yes, went and completed it!") }) {
                    Text("Yes!")
                }
                OutlinedButton(onClick = { onRespond(action.id, "Closed it due to lack of sleep") }) {
                    Text("Slept late")
                }
                OutlinedButton(onClick = { onRespond(action.id, "Feeling sore and tired") }) {
                    Text("Sore")
                }
            }
        }
    }
}

@Composable
private fun ExecutedReceiptCard(
    action: ActionRecord,
    modifier: Modifier = Modifier,
) {
    val hour = action.toolParams["hour"]?.toIntOrNull() ?: 7
    val minute = action.toolParams["minute"]?.toIntOrNull() ?: 0
    val label = action.toolParams["label"] ?: "Aether Routine"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column {
                Text(
                    text = "Alarm Scheduled in Clock",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "%02d:%02d • %s".format(hour, minute, label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EvaluatedReflectionCard(
    reflection: TimelineItem.EvaluatedReflection,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "🧠 Assistant Reflection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (reflection.userReply.isNotBlank()) {
                Text(
                    text = "You replied: \"${reflection.userReply}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            reflection.blocker?.let { blocker ->
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "Diagnosed Blocker: $blocker",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = reflection.reflectionNote
                    ?: "Observed blocker in routine. Adapting upcoming suggestions to remove friction.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DismissedReceiptCard(
    action: ActionRecord,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Routine suggestion dismissed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, modifier: Modifier = Modifier) {
    Text(
        text = date.toDayLabel(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

/**
 * A single memory card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryCard(
    memory: Memory,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true },
                    onLongClickLabel = "Show options",
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = memory.createdAt.toTimeLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (memory.source == MemorySource.MANUAL_VOICE) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "Voice",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    if (memory.type != MemoryType.UNCLASSIFIED) {
                        TypeLabel(type = memory.type)
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Delete") },
                modifier = Modifier.testTag(DashboardTestTags.DELETE_MENU_ITEM),
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun TypeLabel(type: MemoryType, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = type.name.lowercase().replaceFirstChar(Char::uppercase),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing here yet",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Type or speak what's on your mind below, and Aether will quietly assist your routines.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    val now = Instant.parse("2026-07-19T09:30:00Z")
    AetherTheme {
        DashboardContent(
            state = DashboardUiState(
                days = listOf(
                    MemoryDay(
                        date = LocalDate.of(2026, 7, 19),
                        memories = listOf(
                            Memory.capture("Ask about the lease renewal", MemorySource.MANUAL_TEXT, now),
                            Memory.capture("Prefers morning workouts", MemorySource.MANUAL_VOICE, now)
                                .withType(MemoryType.PREFERENCE, now),
                        ),
                    ),
                ),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    AetherTheme { DashboardContent(state = DashboardUiState()) }
}
