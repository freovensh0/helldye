package dev.scdeal.helldye.assistant

/** One row in the conversation: a single turn or a run of consecutive tool messages. */
sealed interface ConversationItem {
    val key: String

    data class Turn(val message: ChatMessage) : ConversationItem {
        override val key: String = message.id
    }

    data class Tools(
        val id: String,
        val messages: List<ChatMessage>,
    ) : ConversationItem {
        override val key: String = "tools-$id"
    }
}

object Conversation {
    /**
     * Groups consecutive tool messages so the overlay can show one summary row instead of a card per
     * tool. A single tool stays a turn, because there is nothing to collapse.
     */
    fun items(messages: List<ChatMessage>): List<ConversationItem> {
        val result = mutableListOf<ConversationItem>()
        val run = mutableListOf<ChatMessage>()

        fun flush() {
            when (run.size) {
                0 -> Unit
                1 -> result += ConversationItem.Turn(run[0])
                else -> result += ConversationItem.Tools(run[0].id, run.toList())
            }
            run.clear()
        }

        for (message in messages) {
            if (message.role == ChatRole.Tool && message.tool != null) {
                run += message
            } else {
                flush()
                result += ConversationItem.Turn(message)
            }
        }
        flush()
        return result
    }

    fun toolSummary(tools: List<ToolInfo>): String {
        val failed = tools.count { it.failed }
        val running = tools.count { it.running }
        return buildString {
            append(tools.size)
            append(if (tools.size == 1) " TOOL" else " TOOLS")
            when {
                failed > 0 -> append(" · ").append(failed).append(" FAILED")
                running > 0 -> append(" · ").append(running).append(" RUNNING")
            }
        }
    }
}
