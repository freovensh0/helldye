package dev.scdeal.helldye.assistant

import java.util.Locale

object ToolSummary {
    fun duration(ms: Long): String =
        if (ms < 1_000) "${ms}ms" else String.format(Locale.US, "%.1fs", ms / 1_000.0)

    /** First meaningful line of the result, falling back to the arguments. */
    fun line(tool: ToolInfo): String {
        val source = tool.result.ifBlank { tool.args }
        return source.lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(160)
            .orEmpty()
    }
}
