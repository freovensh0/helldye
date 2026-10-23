package dev.scdeal.helldye.assistant

object PromptContext {
    fun build(screenText: String?, attachments: List<Attachment>): String? {
        val parts = buildList {
            val hasScreenAttachment = attachments.any { it is Attachment.Screen }
            if (!hasScreenAttachment && !screenText.isNullOrBlank()) {
                add("<screen_context>\n${screenText.take(12_000)}\n</screen_context>")
            }
            attachments.filterIsInstance<Attachment.Screen>().forEach {
                add("<screen_context>\n${it.content.take(12_000)}\n</screen_context>")
            }
            attachments.filterIsInstance<Attachment.Text>().forEach {
                add("<file name=\"${it.name}\">\n${it.content.take(64_000)}\n</file>")
            }
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }
}
