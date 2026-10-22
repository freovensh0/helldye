package dev.scdeal.helldye.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-local handoff from the voice session to the assistant activity.
 * Never persisted: the activity clears it on close.
 */
object AssistInbox {
    private val _screenText = MutableStateFlow<String?>(null)
    val screenText: StateFlow<String?> = _screenText.asStateFlow()

    fun publish(text: String?) {
        _screenText.value = text?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        _screenText.value = null
    }
}
