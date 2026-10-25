package dev.scdeal.helldye.assistant

/**
 * Pure request helpers for the ephemeral screen query. The only screen artifact is the image; no
 * screen text is ever assembled here.
 */
object ScreenQuery {
    const val DEFAULT_PROMPT = "What is on this screen?"

    fun prompt(text: String): String = text.trim().ifEmpty { DEFAULT_PROMPT }

    fun textFiles(attachments: List<Attachment>): List<Attachment.Text> =
        attachments.filterIsInstance<Attachment.Text>()

    fun photos(attachments: List<Attachment>): List<Attachment.Photo> =
        attachments.filterIsInstance<Attachment.Photo>()
}
