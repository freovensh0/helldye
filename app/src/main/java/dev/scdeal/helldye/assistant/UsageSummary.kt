package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayStats
import java.util.Locale

object UsageSummary {
    fun text(
        model: String?,
        thinking: String?,
        stats: GatewayStats?,
        statuses: List<String> = emptyList(),
    ): String {
        val parts = buildList {
            model?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.US)) }
            thinking?.takeIf { it.isNotBlank() }?.let { add("THINK " + it.uppercase(Locale.US)) }
            // Context is the value the composer is read for; keep it before the cumulative totals
            // so it stays visible on a narrow screen.
            if (stats != null && stats.contextWindow > 0) {
                val percent = if (stats.contextPercent >= 0) "${stats.contextPercent}%" else "?"
                val used = compactTokens(stats.contextTokens)
                val limit = compactWindow(stats.contextWindow)
                add("CTX $percent $used/$limit")
            }
            if (stats != null && stats.totalTokens > 0) {
                add(compactTokens(stats.totalTokens) + " TOK")
                if (stats.cost > 0) add(formatCost(stats.cost))
                if (stats.cacheReadTokens > 0) {
                    cachePercent(stats)?.let { add("CACHE $it%") }
                }
            }
            statuses.filter { it.isNotBlank() }.forEach { add(it.uppercase(Locale.US)) }
        }
        return parts.joinToString("  ·  ")
    }

    fun compactTokens(tokens: Long): String = when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
        else -> tokens.toString()
    }

    /** Context windows are round numbers; do not print `200.0K` for them. */
    fun compactWindow(tokens: Long): String = when {
        tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
        tokens >= 1_000 -> "${tokens / 1_000}K"
        else -> tokens.toString()
    }

    fun formatCost(cost: Double): String =
        if (cost >= 1) String.format(Locale.US, "$%.2f", cost)
        else String.format(Locale.US, "$%.4f", cost)

    fun cachePercent(stats: GatewayStats): Int? {
        val prompt = stats.inputTokens + stats.cacheReadTokens
        if (prompt <= 0) return null
        return ((stats.cacheReadTokens * 100) / prompt).toInt()
    }
}
