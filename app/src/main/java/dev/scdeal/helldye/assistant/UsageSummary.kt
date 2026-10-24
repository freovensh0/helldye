package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayStats
import java.util.Locale

object UsageSummary {
    fun text(model: String?, thinking: String?, stats: GatewayStats?): String {
        val parts = buildList {
            model?.takeIf { it.isNotBlank() }?.let { add(it.uppercase(Locale.US)) }
            thinking?.takeIf { it.isNotBlank() }?.let { add("THINK " + it.uppercase(Locale.US)) }
            if (stats != null && stats.totalTokens > 0) {
                add(compactTokens(stats.totalTokens) + " TOK")
                if (stats.cost > 0) add(formatCost(stats.cost))
                if (stats.cacheReadTokens > 0) {
                    cachePercent(stats)?.let { add("CACHE $it%") }
                }
                if (stats.contextPercent >= 0) add("CTX ${stats.contextPercent}%")
            }
        }
        return parts.joinToString("  ·  ")
    }

    fun compactTokens(tokens: Long): String = when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format(Locale.US, "%.1fK", tokens / 1_000.0)
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
