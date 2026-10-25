package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayCommand
import dev.scdeal.helldye.gateway.GatewayModel
import dev.scdeal.helldye.gateway.GatewaySession
import dev.scdeal.helldye.gateway.GatewayStats

enum class ChatRole { User, Assistant, Tool }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val thinking: String = "",
    val tool: ToolInfo? = null,
)

data class ToolInfo(
    val name: String,
    val args: String = "",
    val result: String = "",
    val running: Boolean = true,
    val failed: Boolean = false,
    val durationMs: Long? = null,
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
}

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    /** Transient answer of the current screen query; cleared on close, never sent to /messages. */
    val screenMessages: List<ChatMessage> = emptyList(),
    val screenQuery: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val streaming: Boolean = false,
    /** Abort accepted by the gateway, not yet settled. */
    val stopping: Boolean = false,
    val notice: String? = null,
    val error: String? = null,
    val sessionId: String? = null,
    val models: List<GatewayModel> = emptyList(),
    val commands: List<GatewayCommand> = emptyList(),
    val currentModel: GatewayModel? = null,
    val modelPickerOpen: Boolean = false,
    val capabilitiesLoading: Boolean = false,
    val stats: GatewayStats? = null,
    val thinkingLevel: String? = null,
    val thinkingLevels: List<String> = emptyList(),
    val thinkingPickerOpen: Boolean = false,
    val noticeTone: NoticeTone = NoticeTone.Info,
    val extensionStatus: Map<String, String> = emptyMap(),
    val extensionWidgets: Map<String, ExtensionWidget> = emptyMap(),
    val extensionDialog: ExtensionDialog? = null,
    val editorPrefill: String? = null,
    val queued: Int = 0,
    val compacting: Boolean = false,
    val sessions: List<GatewaySession> = emptyList(),
    val sessionsOpen: Boolean = false,
    val sessionsLoading: Boolean = false,
)
