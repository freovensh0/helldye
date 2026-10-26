package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSearchTest {
    private val models = listOf(
        GatewayModel("anthropic", "claude-sonnet", "Claude Sonnet", reasoning = true, contextWindow = 200_000),
        GatewayModel("openai", "gpt-5", "GPT-5", reasoning = true, contextWindow = 128_000),
        GatewayModel("deepseek", "deepseek-v4", "DeepSeek V4", reasoning = false, contextWindow = 64_000),
    )

    @Test
    fun `blank query keeps every model`() {
        assertEquals(models, ModelSearch.filter(models, "  "))
    }

    @Test
    fun `provider and model text both match`() {
        assertEquals(listOf(models[0]), ModelSearch.filter(models, "anthropic"))
        assertEquals(listOf(models[1]), ModelSearch.filter(models, "gpt-5"))
        assertEquals(listOf(models[2]), ModelSearch.filter(models, "deepseek v4"))
    }

    @Test
    fun `all tokens must match and matching is case insensitive`() {
        assertEquals(listOf(models[0]), ModelSearch.filter(models, "SONNET anth"))
        assertEquals(emptyList<GatewayModel>(), ModelSearch.filter(models, "anthropic gpt"))
    }
}
