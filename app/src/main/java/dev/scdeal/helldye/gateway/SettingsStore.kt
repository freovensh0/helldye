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
    }
}
