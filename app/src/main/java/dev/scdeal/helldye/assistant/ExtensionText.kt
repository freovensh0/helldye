package dev.scdeal.helldye.assistant

object ExtensionText {
    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    private val osc = Regex("\u001B\\][^\u0007]*\u0007")
    private val control = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    /** Extensions may send terminal colors; the Android UI shows plain text only. */
    fun clean(text: String): String =
        control.replace(osc.replace(ansi.replace(text, ""), ""), "").trim()
}
