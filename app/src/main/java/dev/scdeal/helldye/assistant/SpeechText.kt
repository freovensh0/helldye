package dev.scdeal.helldye.assistant

/** Spoken text skips code and collapses markdown spacing; it never reads screen context. */
object SpeechText {
    fun plain(markdown: String): String {
        val withoutFences = Regex("```[\\s\\S]*?```").replace(markdown, " ")
        val withoutInline = Regex("`[^`]*`").replace(withoutFences, " ")
        return withoutInline.replace(Regex("\\s+"), " ").trim()
    }
}
