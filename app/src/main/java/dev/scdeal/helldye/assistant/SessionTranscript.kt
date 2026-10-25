package dev.scdeal.helldye.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Rebuilds the visible conversation from Pi's transcript and reads Pi message fields. */
object SessionTranscript {
    data class Restored(
        val messages: List<ChatMessage>,
        val toolMessageIds: Map<String, String>,
        val trailingAssistantId: String?,
    )

    fun restore(messages: JSONArray): Restored {
        val restored = mutableListOf<ChatMessage>()
        val toolIndexes = mutableMapOf<String, Int>()
        val toolMessageIds = mutableMapOf<String, String>()
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            when (message.optString("role")) {
                "user" -> {
                    val text = UserMessageText.clean(messageText(message))
                    if (text.isNotBlank()) {
                        restored += ChatMessage(
                            UUID.randomUUID().toString(),
                            ChatRole.User,
                            text,
                        )
                    }
                }
                "assistant" -> {
                    val text = messageText(message)
                    val thinking = messageThinking(message)
                    if (text.isNotBlank() || thinking.isNotBlank()) {
                        restored += ChatMessage(
                            UUID.randomUUID().toString(),
                            ChatRole.Assistant,
                            text,
                            thinking,
                        )
                    }
                    val content = message.optJSONArray("content") ?: continue
                    for (blockIndex in 0 until content.length()) {
                        val block = content.optJSONObject(blockIndex) ?: continue
                        if (block.optString("type") != "toolCall") continue
                        val callId = block.optString("id")
                        if (callId.isBlank()) continue
                        val name = block.optString("name")
                        val id = "tool-$callId"
                        toolIndexes[callId] = restored.size
                        toolMessageIds[callId] = id
                        restored += ChatMessage(
                            id = id,
                            role = ChatRole.Tool,
                            text = name,
                            tool = ToolInfo(
                                name = name,
                                args = clip(block.optJSONObject("arguments")?.toString(2).orEmpty()),
                            ),
                        )
                    }
                }
                "toolResult" -> {
                    val callId = message.optString("toolCallId")
                    val at = toolIndexes[callId] ?: continue
                    val tool = restored[at].tool ?: continue
                    restored[at] = restored[at].copy(
                        tool = tool.copy(
                            result = clip(toolResultText(message)),
                            running = false,
                            failed = message.optBoolean("isError"),
                        ),
                    )
                }
            }
        }
        return Restored(
            messages = restored,
            toolMessageIds = toolMessageIds,
            // A trailing assistant message may still be streaming; later deltas append to it.
            trailingAssistantId = restored.lastOrNull()
                ?.takeIf { it.role == ChatRole.Assistant }
                ?.id,
        )
    }

    fun messageText(message: JSONObject): String {
        val content = message.opt("content") ?: return ""
        return when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }
            else -> ""
        }
    }

    fun messageThinking(message: JSONObject): String {
        val content = message.opt("content")
        if (content !is JSONArray) return ""
        return buildString {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") == "thinking") append(block.optString("thinking"))
            }
        }
    }

    fun toolResultText(result: JSONObject?): String {
        if (result == null) return ""
        return when (val content = result.opt("content")) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    val block = content.optJSONObject(index) ?: continue
                    if (block.optString("type") != "text") continue
                    if (isNotEmpty()) append('\n')
                    append(block.optString("text"))
                }
            }
            else -> result.toString(2)
        }
    }

    fun clip(text: String): String =
        if (text.length <= 16_000) text else text.take(16_000) + "\n…"
}
