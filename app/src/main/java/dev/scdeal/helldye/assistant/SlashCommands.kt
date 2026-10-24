package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayCommand

data class SlashCommand(val command: String, val description: String)

object SlashCommands {
    val local = listOf(
        SlashCommand("/model", "Select a model"),
        SlashCommand("/thinking", "Select thinking level"),
        SlashCommand("/new", "Start a new session"),
        SlashCommand("/abort", "Stop the active run"),
        SlashCommand("/help", "Show available commands"),
    )

    fun matches(query: String, discovered: List<GatewayCommand>): List<SlashCommand> {
        if (!query.startsWith("/") || query.contains(' ')) return emptyList()
        val entries = local + discovered
            .filter { it.name.isNotBlank() }
            .map { SlashCommand("/" + it.name, it.description.ifBlank { it.source }) }
        return entries
            .distinctBy { it.command }
            .filter { it.command.startsWith(query, ignoreCase = true) }
    }
}
