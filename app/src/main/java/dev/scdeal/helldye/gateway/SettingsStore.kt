package dev.scdeal.helldye.gateway

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class GatewayConfig(
    val url: String = "",
    val token: String = "",
    val cwd: String = "",
) {
    val fingerprint: String
        get() = "$url|$token|$cwd"
}

/** Survives process death: the last session, its stream cursor, and the gateway epoch it belongs to. */
data class PersistedSession(
    val sessionId: String,
    val lastEventId: String?,
    val fingerprint: String,
    val epoch: String?,
)

class SettingsStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences("helldye", Context.MODE_PRIVATE)
    private val securePreferences: SharedPreferences? by lazy {
        runCatching { createSecurePreferences() }.getOrNull()
    }

    fun isConfigured(): Boolean =
        preferences.getString(KEY_URL, "").orEmpty().isNotBlank()

    fun load(): GatewayConfig = GatewayConfig(
        url = preferences.getString(KEY_URL, "").orEmpty().trim(),
        token = securePreferences?.getString(KEY_TOKEN, "").orEmpty().trim(),
        cwd = preferences.getString(KEY_CWD, "").orEmpty().trim(),
    )

    fun loadSession(): PersistedSession? {
        val secure = securePreferences ?: return null
        val sessionId = secure.getString(KEY_SESSION_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        return PersistedSession(
            sessionId = sessionId,
            lastEventId = secure.getString(KEY_LAST_EVENT_ID, null),
            fingerprint = secure.getString(KEY_SESSION_FINGERPRINT, "").orEmpty(),
            epoch = secure.getString(KEY_SESSION_EPOCH, null),
        )
    }

    fun saveSession(session: PersistedSession) {
        securePreferences?.edit()
            ?.putString(KEY_SESSION_ID, session.sessionId)
            ?.putString(KEY_LAST_EVENT_ID, session.lastEventId)
            ?.putString(KEY_SESSION_FINGERPRINT, session.fingerprint)
            ?.putString(KEY_SESSION_EPOCH, session.epoch)
            ?.apply()
    }

    fun clearSession() {
        securePreferences?.edit()
            ?.remove(KEY_SESSION_ID)
            ?.remove(KEY_LAST_EVENT_ID)
            ?.remove(KEY_SESSION_FINGERPRINT)
            ?.remove(KEY_SESSION_EPOCH)
            ?.apply()
    }

    /** Returns false when the token could not be written to the Android Keystore. */
    fun save(config: GatewayConfig): Boolean {
        preferences.edit()
            .putString(KEY_URL, config.url.trim())
            .putString(KEY_CWD, config.cwd.trim())
            .apply()

        val secure = securePreferences ?: return config.token.isBlank()
        return secure.edit().putString(KEY_TOKEN, config.token.trim()).commit()
    }

    private fun createSecurePreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            applicationContext,
            "helldye_secret",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val KEY_URL = "gateway_url"
        const val KEY_TOKEN = "gateway_token"
        const val KEY_CWD = "gateway_cwd"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_LAST_EVENT_ID = "session_last_event_id"
        const val KEY_SESSION_FINGERPRINT = "session_fingerprint"
        const val KEY_SESSION_EPOCH = "session_epoch"
    }
}
