package dev.scdeal.helldye.assistant

object PromptContext {
    fun build(attachments: List<Attachment>): String? {
        val files = attachments.filterIsInstance<Attachment.Text>()
        if (files.isEmpty()) return null
        return files.joinToString("\n\n") {
            "<file name=\"${it.name}\">\n${it.content.take(64_000)}\n</file>"
        }
    }
}
