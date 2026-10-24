package dev.scdeal.helldye.assistant

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local handoff from the voice session to the assistant UI.
 * The screenshot is only used when the user explicitly attaches the Screen.
 */
object AssistInbox {
    private val _screenshot = MutableStateFlow<Bitmap?>(null)
    val screenshot: StateFlow<Bitmap?> = _screenshot.asStateFlow()

    private val _assistReceived = MutableStateFlow(false)
    val assistReceived: StateFlow<Boolean> = _assistReceived.asStateFlow()

    fun markAssistReceived() {
        _assistReceived.value = true
    }

    fun publishScreenshot(bitmap: Bitmap?) {
        _screenshot.value = bitmap
    }

    fun clear() {
        _screenshot.value = null
        _assistReceived.value = false
    }
}
