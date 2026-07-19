package com.aether.tools.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * What the recogniser is currently telling us.
 *
 * [Partial] results arrive continuously while speaking. They matter for more
 * than decoration: without visible feedback a long pause reads as the app
 * having frozen, and the user stops trusting voice capture.
 */
sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Failed(val reason: String) : SpeechEvent
}

/**
 * Wraps Android's [SpeechRecognizer] as a Flow.
 *
 * Aether is offline-first, so recognition asks to stay on-device. That request
 * is a preference, not a guarantee — whether it is honoured depends on the
 * recogniser and installed language packs — so callers must treat text capture
 * as the always-available path and voice as an accelerator.
 *
 * [SpeechRecognizer] is main-thread-only and must be released, both of which
 * callbackFlow handles: creation happens in the collector's context and
 * awaitClose tears it down on cancellation.
 */
class SpeechToText(private val context: Context) {

    /** False when no recogniser is installed; the mic affordance should be hidden. */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun transcribe(): Flow<SpeechEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(SpeechEvent.Failed("Speech recognition is not available on this device"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val listener = object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstTranscript()?.let { trySend(SpeechEvent.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val transcript = results.firstTranscript()
                if (transcript.isNullOrBlank()) {
                    trySend(SpeechEvent.Failed("Didn't catch that"))
                } else {
                    trySend(SpeechEvent.Final(transcript))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(error.toMessage()))
                close()
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        recognizer.setRecognitionListener(listener)
        recognizer.startListening(recognitionIntent())

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // A request, not a guarantee: honoured only when the recogniser
                // has an on-device language pack available.
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }

    private fun Bundle?.firstTranscript(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    /**
     * Turns recogniser error codes into something a person can act on.
     *
     * Generic "something went wrong" text would leave the user with no idea
     * whether to speak louder, grant a permission or give up and type.
     */
    private fun Int.toMessage(): String = when (this) {
        SpeechRecognizer.ERROR_AUDIO -> "Couldn't record audio"
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition stopped unexpectedly"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed"
        SpeechRecognizer.ERROR_NETWORK -> "This recogniser needs a network connection"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out"
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy, try again"
        SpeechRecognizer.ERROR_SERVER -> "Speech service returned an error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything"
        else -> "Speech recognition failed"
    }
}
