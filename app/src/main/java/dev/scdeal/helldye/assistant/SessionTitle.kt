package dev.scdeal.helldye.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Neutral gateway names are IDs; show a timestamp instead unless the operator renamed the session. */
object SessionTitle {
    private const val NEUTRAL_PREFIX = "Session "

    fun display(name: String?, created: Long): String {
        val raw = name.orEmpty().trim()
        if (raw.isNotEmpty() && !raw.startsWith(NEUTRAL_PREFIX)) return raw
        if (created > 0) {
            return SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(created * 1000))
        }
        return raw.ifBlank { "SESSION" }
    }
}
