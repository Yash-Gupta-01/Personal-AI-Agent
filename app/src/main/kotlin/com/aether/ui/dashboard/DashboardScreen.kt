package com.aether.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aether.domain.memory.Memory
import com.aether.domain.memory.MemoryDay
import com.aether.domain.memory.MemorySource
import com.aether.domain.memory.MemoryType
import com.aether.ui.theme.AetherTheme
import java.time.Instant
import java.time.LocalDate

/**
 * The home screen: what Aether currently knows, newest first.
 *
 * Deliberately not a chat screen. A chat log shows what you said; this shows
 * what Aether remembers, which is the thing worth opening the app for.
 */
@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onDelete: (Memory) -> Unit = {},
) {
    if (state.isEmpty) {
        EmptyState(modifier = modifier.padding(contentPadding))
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.days.forEach { day ->
            item(key = "header-${day.date}") {
                DayHeader(day = day)
            }
            items(items = day.memories, key = { it.id }) { memory ->
                MemoryCard(memory = memory, onDelete = { onDelete(memory) })
            }
        }
    }
}

@Composable
private fun DayHeader(day: MemoryDay, modifier: Modifier = Modifier) {
    Text(
        text = day.date.toDayLabel(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // Marked as a heading so screen readers can jump between days.
            .semantics { heading() },
    )
}

/**
 * A single memory.
 *
 * Delete is a long-press with an overflow menu rather than a swipe. A swipe
 * would need a visible non-gesture alternative anyway to remain usable with
 * assistive tech, so it is strictly more code for the same outcome.
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
                    // Nothing is shown while a memory is unclassified. A
                    // "pending" chip would advertise machinery the user did
                    // not ask for and cannot act on; the type appearing on its
                    // own is the whole point.
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

/**
 * The type a memory was classified as.
 *
 * Text, not an icon: nine types have no icon set anyone would read correctly,
 * and a wrong-looking glyph is harder to ignore than a word. Not a chip
 * either — chips look tappable, and this is a statement, not an action.
 */
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

/**
 * First-run guidance.
 *
 * Aether has no onboarding flow: its core action is obvious, so a carousel
 * would be skipped and would delay the first capture. The empty state teaches
 * the one thing worth knowing, exactly when it is relevant.
 */
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
            text = "Capture a thought and Aether will start remembering it for you.",
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
