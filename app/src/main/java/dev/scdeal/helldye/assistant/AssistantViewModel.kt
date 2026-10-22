package dev.scdeal.helldye.assistant

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantPhase(val status: String, val hint: String) {
    Ready("READY", "Type a request. Screen text is read only when you ask."),
    Thinking("THINKING", "Preparing a response."),
    Answer("ANSWER", "Response ready."),
    Error("ERROR", "Something needs attention."),
}

data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.Ready,
    val transcript: String = "",
    val answer: String = "",
    val notice: String? = null,
)

class AssistantViewModel : ViewModel() {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    fun submit(text: String, screenText: String?) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return

        val contextNote = if (screenText.isNullOrBlank()) {
            "No screen context was attached."
        } else {
            "Screen context received: ${screenText.length} characters."
        }
        _state.value = AssistantUiState(
            phase = AssistantPhase.Answer,
            transcript = prompt,
            answer = "The Pi gateway is not connected yet. $contextNote",
        )
    }

    fun onMic() {
        _state.value = _state.value.copy(
            notice = "Voice input is not connected yet. Use TYPE for now.",
        )
    }

    fun dismissNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}
