package dev.scdeal.helldye.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

class SpeechInput(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val fail: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            onListening(false)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) fail("No speech detected.") else onResult(text)
        }

        override fun onError(error: Int) {
            onListening(false)
            fail(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service is offline."
                    else -> "Speech recognition failed."
                },
            )
        }

        override fun onReadyForSpeech(params: Bundle?) = onListening(true)

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = onListening(false)

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    fun start() {
        val engine = recognizer ?: create()?.also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        if (engine == null) {
            fail("Speech recognition is unavailable.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { engine.startListening(intent) }
            .onFailure {
                onListening(false)
                fail("Speech recognition is unavailable.")
            }
    }

    fun destroy() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun create(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrNull()
                ?.let { return it }
        }
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }
}

@Composable
fun rememberSpeechInput(
    onResult: (String) -> Unit,
    onListening: (Boolean) -> Unit,
    onError: (String) -> Unit,
): SpeechInput {
    val context = LocalContext.current.applicationContext
    val result by rememberUpdatedState(onResult)
    val listening by rememberUpdatedState(onListening)
    val error by rememberUpdatedState(onError)
    val speech = remember(context) {
        SpeechInput(context, { result(it) }, { listening(it) }, { error(it) })
    }
    DisposableEffect(speech) { onDispose { speech.destroy() } }
    return speech
}
