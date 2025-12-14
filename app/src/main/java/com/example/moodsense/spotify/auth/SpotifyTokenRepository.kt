package com.example.moodsense.spotify.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.moodsense.spotify.DebugLog

class SpotifyTokenRepository(context: Context) {
    data class TokenState(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val scope: String?
    ) {
        fun isExpired(leewaySeconds: Long = DEFAULT_LEEWAY_SECONDS): Boolean {
            return System.currentTimeMillis() >= expiresAtMillis - leewaySeconds * 1000
        }

        fun expiresInSeconds(): Long = (expiresAtMillis - System.currentTimeMillis()) / 1000

        companion object {
            private const val DEFAULT_LEEWAY_SECONDS = 60L
        }
    }

    data class PendingAuthorization(val state: String, val codeVerifier: String)

    private val prefs: SharedPreferences = createPreferences(context)

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, scope: String?) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_SCOPE, scope)
            .apply()
    }

    fun readTokens(): TokenState? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val scope = prefs.getString(KEY_SCOPE, null)
        return TokenState(accessToken, refreshToken, expiresAt, scope)
    }

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_SCOPE)
            .apply()
    }

    fun savePendingAuthorization(state: String, codeVerifier: String) {
        prefs.edit()
            .putString(KEY_PENDING_STATE, state)
            .putString(KEY_PENDING_CODE_VERIFIER, codeVerifier)
            .apply()
    }

    fun getPendingAuthorization(): PendingAuthorization? {
        val state = prefs.getString(KEY_PENDING_STATE, null) ?: return null
        val codeVerifier = prefs.getString(KEY_PENDING_CODE_VERIFIER, null) ?: return null
        return PendingAuthorization(state, codeVerifier)
    }

    fun clearPendingAuthorization() {
        prefs.edit()
            .remove(KEY_PENDING_STATE)
            .remove(KEY_PENDING_CODE_VERIFIER)
            .apply()
    }

    fun describe(): String {
        val token = readTokens() ?: return "No token stored"
        val expiresIn = token.expiresInSeconds()
        val scopeLabel = token.scope ?: "(unspecified)"
        val refreshLabel = if (token.refreshToken.isNullOrBlank()) "none" else "present"
        return "Access token expires in ${expiresIn}s, scope=$scopeLabel, refresh=$refreshLabel"
    }

    private fun createPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            DebugLog.e(TAG, "Falling back to SharedPreferences for token storage", e)
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val PREF_NAME = "spotify_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SCOPE = "scope"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_PENDING_CODE_VERIFIER = "pending_code_verifier"
        private const val TAG = "SpotifyTokenRepo"
    }
}
