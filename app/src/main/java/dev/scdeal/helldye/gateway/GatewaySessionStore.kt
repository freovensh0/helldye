package dev.scdeal.helldye.gateway

object GatewaySessionStore {
    @Volatile
    var sessionId: String? = null

    @Volatile
    var lastEventId: String? = null

    @Volatile
    var fingerprint: String? = null

    fun reset() {
        sessionId = null
        lastEventId = null
        fingerprint = null
    }
}
