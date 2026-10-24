package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageSummaryTest {
    private val stats = GatewayStats(
        inputTokens = 15_000,
        outputTokens = 2_000,
        cacheReadTokens = 45_000,
        cacheWriteTokens = 1_000,
        totalTokens = 63_000,
        cost = 0.0423,
        contextTokens = 60_000,
        contextWindow = 200_000,
        contextPercent = 30,
    )

    @Test
    fun `usage line matches the Pi footer`() {
        assertEquals(
            "CLAUDE SONNET  ·  THINK HIGH  ·  63.0K TOK  ·  \$0.0423  ·  CACHE 75%  ·  CTX 30%",
            UsageSummary.text("Claude Sonnet", "high", stats),
        )
    }

    @Test
    fun `model only is shown before the first turn`() {
        assertEquals(
            "SONNET  ·  THINK OFF",
            UsageSummary.text("Sonnet", "off", GatewayStats()),
        )
        assertEquals("", UsageSummary.text(null, null, null))
    }

    @Test
    fun `cache line is hidden without cache reads`() {
        assertEquals(
            "SONNET  ·  1.2K TOK",
            UsageSummary.text(
                "Sonnet",
                null,
                GatewayStats(inputTokens = 1_200, totalTokens = 1_200),
            ),
        )
    }

    @Test
    fun `cache hit is null without prompt tokens`() {
        assertNull(UsageSummary.cachePercent(GatewayStats()))
        assertEquals(100, UsageSummary.cachePercent(GatewayStats(cacheReadTokens = 500)))
    }

    @Test
    fun `tokens and cost are compacted`() {
        assertEquals("999", UsageSummary.compactTokens(999))
        assertEquals("1.5K", UsageSummary.compactTokens(1_500))
        assertEquals("2.0M", UsageSummary.compactTokens(2_000_000))
        assertEquals("\$0.0423", UsageSummary.formatCost(0.0423))
        assertEquals("\$1.50", UsageSummary.formatCost(1.5))
    }
}
