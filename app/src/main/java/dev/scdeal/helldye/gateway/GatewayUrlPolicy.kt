package dev.scdeal.helldye.gateway

import java.net.URI

/**
 * Release builds allow HTTPS only. Cleartext HTTP is limited to a loopback gateway in a debug build,
 * matching `app/src/debug/res/xml/network_security_config.xml`.
 */
object GatewayUrlPolicy {
    fun validate(url: String, debug: Boolean): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "GATEWAY URL IS EMPTY"
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return "GATEWAY URL IS INVALID"
        val scheme = uri.scheme?.lowercase() ?: return "GATEWAY URL MUST START WITH https://"
        if (scheme != "http" && scheme != "https") return "GATEWAY URL MUST USE https://"
        val host = uri.host ?: return "GATEWAY URL HAS NO HOST"
        if (scheme == "http" && !isLoopback(host)) {
            return if (debug) {
                "HTTP IS ONLY ALLOWED FOR 127.0.0.1 OR localhost"
            } else {
                "USE HTTPS. HTTP IS ONLY ALLOWED FOR 127.0.0.1 OR localhost"
            }
        }
        return null
    }

    fun isLoopback(host: String): Boolean {
        val normalized = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        return normalized == "localhost" || normalized == "127.0.0.1" || normalized == "::1"
    }
}
