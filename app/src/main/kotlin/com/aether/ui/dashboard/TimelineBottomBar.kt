package com.aether.ui.dashboard

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aether.domain.memory.MemorySource
import com.aether.ui.capture.rememberVoiceCapture

/**
 * An inline bottom capture bar for the assistant stream.
 *
 * Allows instant text typing or voice dictation without leaving the main
 * conversation timeline or opening modal sheets.
 */
@Composable
internal fun TimelineBottomBar(
    onCapture: (String, MemorySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var usedVoice by remember { mutableStateOf(false) }

    val voice = rememberVoiceCapture(
        onTranscript = { transcript ->
            text = transcript
            usedVoice = true
        },
        onError = { /* silence recognition error */ },
    )

    val send = {
        if (text.isNotBlank()) {
            val source = if (usedVoice) MemorySource.MANUAL_VOICE else MemorySource.MANUAL_TEXT
            onCapture(text.trim(), source)
            text = ""
            usedVoice = false
            if (voice.isListening) {
                voice.onToggle()
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (voice.isListening) {
                        voice.onToggle()
                    }
                },
                placeholder = {
                    Text(
                        text = if (voice.isListening) "Listening..." else "What's on your mind? (e.g. 'missed gym')...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (voice.isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )

            // Voice Mic Button
            if (voice.isAvailable) {
                IconButton(
                    onClick = { voice.onToggle() },
                    colors = if (voice.isListening) {
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        IconButtonDefaults.iconButtonColors()
                    },
                ) {
                    Text(
                        text = if (voice.isListening) "🔴" else "🎙️",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Capture / Send Button
            FilledIconButton(
                onClick = { send() },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag(DashboardTestTags.CAPTURE_FAB),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save thought",
                )
            }
        }
    }
}
