package dev.scdeal.helldye.gateway

/**
 * The active session and its stream cursor. Values are mirrored into encrypted preferences so a
 * process death does not lose the session; the in-memory values stay the fast path.
 *
 * A cursor is only meaningful for the epoch it was produced in; [epoch] is stored with it.
 */
object GatewaySessionStore {
    private var store: SettingsStore? = null
    private var restoring = false

    @Volatile
    var sessionId: String? = null
        set(value) {
            field = value
            persist()
        }

    @Volatile
    var lastEventId: String? = null
        set(value) {
            field = value
            persist()
        }

    @Volatile
    var fingerprint: String? = null
        set(value) {
            field = value
            persist()
        }

    @Volatile
    var epoch: String? = null
        set(value) {
            field = value
            persist()
        }

    /** Restores the persisted cursor once. The caller still validates the config fingerprint. */
    fun attach(store: SettingsStore) {
        this.store = store
        if (sessionId != null) return
        val saved = store.loadSession() ?: return
        restoring = true
        sessionId = saved.sessionId
        lastEventId = saved.lastEventId
        fingerprint = saved.fingerprint
        epoch = saved.epoch
        restoring = false
    }

    /** A cursor only resumes within the epoch that produced it. */
    fun reconcileCursor(storedEpoch: String?, stateEpoch: String?, cursor: String?): String? =
        if (storedEpoch != null && stateEpoch != null && storedEpoch != stateEpoch) null else cursor

    fun reset() {
        sessionId = null
        lastEventId = null
        fingerprint = null
        epoch = null
    }

    private fun persist() {
        if (restoring) return
        val store = store ?: return
        val id = sessionId
        if (id == null) {
            store.clearSession()
            return
        }
        store.saveSession(
            PersistedSession(
                sessionId = id,
                lastEventId = lastEventId,
                fingerprint = fingerprint.orEmpty(),
                epoch = epoch,
            ),
        )
    }
}
