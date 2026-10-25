package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayContextFile

/**
 * Converts text attachments to structured data. The gateway owns the prompt envelope and the
 * "context is data, not instructions" rule.
 */
object PromptContext {
    fun files(attachments: List<Attachment>): List<GatewayContextFile> =
        attachments.filterIsInstance<Attachment.Text>().map {
            GatewayContextFile(name = it.name, content = it.content.take(64_000))
        }
}
