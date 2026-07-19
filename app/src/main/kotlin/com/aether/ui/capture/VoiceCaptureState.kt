package com.aether.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.aether.tools.speech.SpeechEvent
import com.aether.tools.speech.SpeechToText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Drives voice capture for the capture sheet.
 *
 * Speech is treated as an input method — like the soft keyboard — rather than
 * as a domain concern, so it lives at the UI layer and never reaches the
 * ViewModel. Text capture stays the always-available path; if recognition is
 * unavailable or denied, the mic simply is not offered and nothing else
 * changes.
 */
internal class VoiceCaptureController(
    private val speechToText: SpeechToText,
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    var isListening by mutableStateOf(false)
        private set

    private var job: Job? = null

    val isAvailable: Boolean get() = speechToText.isAvailable()

    fun start(scope: kotlinx.coroutines.CoroutineScope) {
        if (isListening) return
        isListening = true
        job = speechToText.transcribe()
            .onEach { event ->
                when (event) {
                    // Partial results stream into the field as you speak, so a
                    // pause never looks like a freeze.
                    is SpeechEvent.Partial -> onTranscript(event.text)
                    is SpeechEvent.Final -> {
                        onTranscript(event.text)
                        isListening = false
                    }

                    is SpeechEvent.Failed -> {
                        onError(event.reason)
                        isListening = false
                    }
                }
            }
            .launchIn(scope)
    }

    fun stop() {
        job?.cancel()
        job = null
        isListening = false
    }
}

/**
 * Wires up voice capture, requesting the microphone only when the user first
 * taps the mic.
 *
 * The permission is requested at the moment of use, where the reason is
 * self-evident, rather than up front where it would arrive without context.
 */
@Composable
internal fun rememberVoiceCapture(
    onTranscript: (String) -> Unit,
    onError: (String) -> Unit,
): VoiceCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechToText = remember(context) { SpeechToText(context) }

    val controller = remember(speechToText) {
        VoiceCaptureController(speechToText, onTranscript, onError)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            controller.start(scope)
        } else {
            onError("Microphone permission is needed for voice capture")
        }
    }

    return remember(controller) {
        VoiceCapture(
            isAvailable = controller.isAvailable,
            isListeningProvider = { controller.isListening },
            onToggle = {
                if (controller.isListening) {
                    controller.stop()
                } else {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED

                    if (granted) {
                        scope.launch { controller.start(scope) }
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            },
        )
    }
}

internal class VoiceCapture(
    val isAvailable: Boolean,
    private val isListeningProvider: () -> Boolean,
    val onToggle: () -> Unit,
) {
    val isListening: Boolean get() = isListeningProvider()
}
