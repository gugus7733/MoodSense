package com.example.moodsense.spotify.auth

import android.app.Activity
import android.net.Uri
import com.example.moodsense.spotify.DebugLog
import com.example.moodsense.spotify.SpotifyDiagnostics
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SpotifyAuthManager(
    private val activity: Activity,
    private val clientId: String,
    private val redirectUri: String,
    private val tokenRepository: SpotifyTokenRepository,
    private val diagnostics: SpotifyDiagnostics,
    private val scopes: List<String>
) {
    data class AuthResult(val tokens: SpotifyTokenRepository.TokenState)

    sealed class RedirectResult {
        data class Success(val code: String) : RedirectResult()
        object Ignored : RedirectResult()
        data class Error(val message: String) : RedirectResult()
    }

    fun startAuthorization() {
        diagnostics.state.lastAuthStatus = "Opening Spotify login (PKCE)"
        val codeVerifier = PkceUtil.generateCodeVerifier()
        val codeChallenge = PkceUtil.generateCodeChallenge(codeVerifier)
        val state = PkceUtil.generateState()
        tokenRepository.savePendingAuthorization(state, codeVerifier)

        val request = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.CODE,
            redirectUri
        )
            .setScopes(scopes.toTypedArray())
            .setShowDialog(true)
            .setState(state)
            .setCustomParam("code_challenge", codeChallenge)
            .setCustomParam("code_challenge_method", CODE_CHALLENGE_METHOD)
            .build()

        DebugLog.i(TAG, "Launching browser auth for scopes=${scopes.joinToString()}")
        AuthorizationClient.openLoginInBrowser(activity, request)
    }

    fun handleRedirect(uri: Uri?): RedirectResult {
        if (uri == null) return RedirectResult.Ignored
        val expected = Uri.parse(redirectUri)
        if (uri.scheme != expected.scheme || uri.host != expected.host) {
            return RedirectResult.Ignored
        }
        diagnostics.markRedirectReceived(uri.toString())
        val pendingAuth = tokenRepository.getPendingAuthorization()
            ?: return RedirectResult.Error("No pending auth state; please retry login")
        val returnedState = uri.getQueryParameter("state")
        if (returnedState.isNullOrBlank() || returnedState != pendingAuth.state) {
            return RedirectResult.Error("State mismatch; discarding response")
        }
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return RedirectResult.Error("Auth error: $error")
        }
        val code = uri.getQueryParameter("code") ?: return RedirectResult.Error("Missing authorization code")
        return RedirectResult.Success(code)
    }

    suspend fun exchangeCodeForTokens(code: String): Result<AuthResult> {
        val pendingAuth = tokenRepository.getPendingAuthorization()
            ?: return Result.failure(IllegalStateException("No PKCE verifier stored for exchange"))
        return performTokenRequest(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to pendingAuth.codeVerifier
            )
        ).onSuccess {
            tokenRepository.clearPendingAuthorization()
            diagnostics.state.lastAuthStatus = "Tokens received"
        }.map { AuthResult(it) }
    }

    suspend fun refreshTokensIfNeeded(): Result<SpotifyTokenRepository.TokenState> {
        val current = tokenRepository.readTokens() ?: return Result.failure(IllegalStateException("No stored token"))
        return if (!current.isExpired()) {
            Result.success(current)
        } else {
            refreshTokens(current)
        }
    }

    suspend fun refreshTokens(forceCurrent: SpotifyTokenRepository.TokenState? = null): Result<SpotifyTokenRepository.TokenState> {
        val current = forceCurrent ?: tokenRepository.readTokens()
        val refreshToken = current?.refreshToken
            ?: return Result.failure(IllegalStateException("No refresh token available"))
        diagnostics.state.lastAuthStatus = "Refreshing token"
        return performTokenRequest(
            mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to clientId
            )
        ).map { updated ->
            val nextRefresh = updated.refreshToken ?: refreshToken
            val merged = updated.copy(refreshToken = nextRefresh)
            tokenRepository.saveTokens(
                accessToken = merged.accessToken,
                refreshToken = merged.refreshToken,
                expiresInSeconds = (merged.expiresAtMillis - System.currentTimeMillis()) / 1000,
                scope = merged.scope
            )
            diagnostics.state.lastAuthStatus = "Token refreshed"
            merged
        }
    }

    fun clearTokens() {
        tokenRepository.clearTokens()
        diagnostics.state.lastAuthStatus = "Tokens cleared"
    }

    private suspend fun performTokenRequest(params: Map<String, String>): Result<SpotifyTokenRepository.TokenState> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(TOKEN_ENDPOINT)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                val body = params.entries.joinToString("&") { (key, value) ->
                    "${URLEncoder.encode(key, "UTF-8")}" + "=" + URLEncoder.encode(value, "UTF-8")
                }
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                }
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.readText()
                }
                if (responseCode !in 200..299) {
                    DebugLog.e(TAG, "Token request failed [$responseCode]: $responseText")
                    return@withContext Result.failure(IllegalStateException("Token request failed: $responseCode"))
                }
                val json = JSONObject(responseText)
                val accessToken = json.getString("access_token")
                val expiresIn = json.getLong("expires_in")
                val refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null
                val scope = if (json.has("scope")) json.getString("scope") else null
                val expiresAt = System.currentTimeMillis() + expiresIn * 1000
                val tokenState = SpotifyTokenRepository.TokenState(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresAtMillis = expiresAt,
                    scope = scope
                )
                tokenRepository.saveTokens(accessToken, refreshToken, expiresIn, scope)
                diagnostics.state.lastAuthStatus = "Tokens stored"
                Result.success(tokenState)
            } catch (e: Exception) {
                DebugLog.e(TAG, "Exception during token request", e)
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val TAG = "SpotifyAuthManager"
        private const val CODE_CHALLENGE_METHOD = "S256"
        private const val TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token"
    }
}
