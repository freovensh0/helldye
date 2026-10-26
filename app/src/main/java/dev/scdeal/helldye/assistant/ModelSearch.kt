package dev.scdeal.helldye.assistant

import dev.scdeal.helldye.gateway.GatewayModel
import java.util.Locale

/** Case-insensitive token search across provider, model name, and model id. */
object ModelSearch {
    fun filter(models: List<GatewayModel>, query: String): List<GatewayModel> {
        val tokens = query.trim().lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return models
        return models.filter { model ->
            val haystack = "${model.provider} ${model.name} ${model.id}".lowercase(Locale.ROOT)
            tokens.all { haystack.contains(it) }
        }
    }
}
