package dev.scdeal.helldye.assistant

object UserMessageText {
    private val contextBlock = Regex(
        "<(?:screen_context|file)(?:\\s[^>]*)?>.*?</(?:screen_context|file)>",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun clean(text: String): String =
        contextBlock.replace(text, "").trim().ifBlank { "Use the attached context." }
}
