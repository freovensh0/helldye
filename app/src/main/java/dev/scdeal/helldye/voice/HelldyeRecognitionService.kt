package dev.scdeal.helldye.voice

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Placeholder so VoiceInteractionService metadata points at a real recognition component.
 * Voice capture is not connected yet; finish the request with a client error instead of hanging.
 */
class HelldyeRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: RecognitionService.Callback) {
        runCatching { listener.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onStopListening(listener: RecognitionService.Callback) = Unit

    override fun onCancel(listener: RecognitionService.Callback) = Unit
}
