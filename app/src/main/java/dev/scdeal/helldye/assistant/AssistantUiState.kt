package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayCommand
import dev.scdeal.helldye.gateway.GatewayDiff
import dev.scdeal.helldye.gateway.GatewayDiffFile
import dev.scdeal.helldye.gateway.GatewayModel
import dev.scdeal.helldye.gateway.GatewayTree
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
    /** Device has no default network. Drafts are preserved; nothing is auto-sent. */
    val offline: Boolean = false,
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
    val hasOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    val diffOpen: Boolean = false,
    val diffLoading: Boolean = false,
    val diff: GatewayDiff? = null,
    val diffError: String? = null,
    /** Dirty workspace files after a turn; drives the summary above the composer. */
    val changedFiles: List<GatewayDiffFile> = emptyList(),
    val treeOpen: Boolean = false,
    val treeLoading: Boolean = false,
    val tree: GatewayTree? = null,
    val treeError: String? = null,
    /** Set after a successful export fetch; the screen launches SAF and then calls consumeExport. */
    val exportHtml: String? = null,
    val sessions: List<GatewaySession> = emptyList(),
    val sessionsOpen: Boolean = false,
    val sessionsLoading: Boolean = false,
)
