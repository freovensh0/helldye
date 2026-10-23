package dev.scdeal.helldye.assistant

enum class ChatRole { User, Assistant, Tool }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
)

sealed interface Attachment {
    val label: String

    data class Photo(
        val name: String,
        val mimeType: String,
        val base64: String,
    ) : Attachment {
        override val label: String = name
    }

    data class Text(
        val name: String,
        val content: String,
    ) : Attachment {
        override val label: String = name
    }

    data class Screen(val content: String) : Attachment {
        override val label: String = "SCREEN"
    }
}

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val streaming: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
    val sessionId: String? = null,
)
