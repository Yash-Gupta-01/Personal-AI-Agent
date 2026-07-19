package com.aether.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aether.domain.memory.MemorySource

object CaptureTestTags {
    const val TEXT_FIELD = "capture_text_field"
    const val SAVE_BUTTON = "capture_save_button"
    const val MIC_BUTTON = "capture_mic_button"
}

/**
 * The capture surface: type a thought, save it, done.
 *
 * There is no category picker, no title field and no tags. Every extra
 * decision at capture time is a reason to not bother capturing at all, and
 * classification is the memory agent's job — not the user's.
 *
 * The field takes focus immediately so the keyboard is already up when the
 * sheet settles; capture should cost one tap and typing, nothing more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickCaptureSheet(
    onDismiss: () -> Unit,
    onCapture: (String, MemorySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Provenance: once speech produced the text, the memory is a voice capture
    // even if it was then edited by hand. Voice transcripts carry recognition
    // errors that typed text does not, and the memory agent will need to know.
    var usedVoice by remember { mutableStateOf(false) }

    val voice = rememberVoiceCapture(
        onTranscript = { transcript ->
            text = transcript
            usedVoice = true
            error = null
        },
        onError = { error = it },
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val canSave = text.isNotBlank()
    val save = {
        if (canSave) {
            onCapture(
                text,
                if (usedVoice) MemorySource.MANUAL_VOICE else MemorySource.MANUAL_TEXT,
            )
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                // Keeps the field and button above the keyboard and gesture bar.
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("What's on your mind?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag(CaptureTestTags.TEXT_FIELD),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Done on the keyboard saves, so capture never requires
                // reaching for a button.
                keyboardActions = KeyboardActions(onDone = { save() }),
            )

            error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Hidden entirely when no recogniser exists, rather than shown
                // disabled: an affordance that can never work is just noise.
                if (voice.isAvailable) {
                    // A labelled button rather than a mic icon: the icon lives
                    // only in material-icons-extended, which is thousands of
                    // vectors of APK for one glyph, and the label is clearer.
                    TextButton(
                        onClick = voice.onToggle,
                        modifier = Modifier.testTag(CaptureTestTags.MIC_BUTTON),
                    ) {
                        Text(
                            text = if (voice.isListening) "Listening… tap to stop" else "Speak",
                            color = if (voice.isListening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(0.dp))
                }

                Button(
                    onClick = save,
                    enabled = canSave,
                    modifier = Modifier.testTag(CaptureTestTags.SAVE_BUTTON),
                ) {
                    Text("Capture")
                }
            }
        }
    }
}
