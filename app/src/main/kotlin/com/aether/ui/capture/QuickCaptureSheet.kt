package com.aether.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

object CaptureTestTags {
    const val TEXT_FIELD = "capture_text_field"
    const val SAVE_BUTTON = "capture_save_button"
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
    onCapture: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val canSave = text.isNotBlank()
    val save = {
        if (canSave) {
            onCapture(text)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
