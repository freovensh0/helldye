package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandsTest {
    @Test
    fun `local commands are filtered by prefix`() {
        assertEquals(
            listOf(SlashCommand("/model", "Select a model")),
            SlashCommands.matches("/mo", emptyList()),
        )
        assertEquals(SlashCommands.local, SlashCommands.matches("/", emptyList()))
    }

    @Test
    fun `a query with a space is not a slash command`() {
        assertTrue(SlashCommands.matches("/model gpt", emptyList()).isEmpty())
        assertTrue(SlashCommands.matches("hello", emptyList()).isEmpty())
    }

    @Test
    fun `discovered commands are prefixed and deduplicated`() {
        val discovered = listOf(
            GatewayCommand("fix-tests", "Fix failing tests", "prompt"),
            GatewayCommand("model", "Duplicate", "extension"),
        )
        assertEquals(
            listOf(SlashCommand("/fix-tests", "Fix failing tests")),
            SlashCommands.matches("/fix", discovered),
        )
        assertEquals(
            SlashCommands.local + SlashCommand("/fix-tests", "Fix failing tests"),
            SlashCommands.matches("/", discovered),
        )
    }
}
