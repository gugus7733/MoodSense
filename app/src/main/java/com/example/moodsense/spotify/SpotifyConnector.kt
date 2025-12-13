package com.example.moodsense.spotify

import android.app.Activity
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlin.math.min

class SpotifyConnector(
    private val activity: Activity,
    private val clientId: String,
    private val redirectUri: String,
    private val diagnostics: SpotifyDiagnostics,
    private val listener: Listener
) {
    data class RetryConfig(val maxAttempts: Int = 2, val backoffMillis: List<Long> = listOf(0L, 500L, 1500L))

    interface Listener {
        fun onStatusChanged(status: String)
        fun onConnected(appRemote: SpotifyAppRemote)
    }

    private var accessToken: String? = null
    private var connectionAttempt = 0
    private val retryConfig = RetryConfig()

    fun startAuthorization(authRequestCode: Int) {
        diagnostics.state.lastAuthStatus = "Requesting token via implicit grant"
        connectionAttempt = 0
        listener.onStatusChanged("Requesting Spotify authorization...")
        val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
        builder.setScopes(arrayOf("streaming", "user-read-playback-state", "user-modify-playback-state"))
        val request = builder.build()
        DebugLog.i(TAG, "Opening Spotify login activity with redirect $redirectUri")
        AuthorizationClient.openLoginActivity(activity, authRequestCode, request)
    }

    fun handleAuthorizationResponse(response: AuthorizationResponse, authRequestCode: Int) {
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                accessToken = response.accessToken
                diagnostics.state.lastAuthStatus = "Token received (expires in ${response.expiresIn} seconds)"
                listener.onStatusChanged("Token received, connecting to Spotify app...")
                DebugLog.i(TAG, "Received access token, attempting app remote connection")
                attemptConnect(authRequestCode)
            }

            AuthorizationResponse.Type.ERROR -> {
                diagnostics.state.lastAuthStatus = "Auth error: ${response.error}"
                diagnostics.state.lastError = response.error ?: "Unknown auth error"
                DebugLog.e(TAG, "Auth error: ${response.error}")
                listener.onStatusChanged("Auth error: ${response.error}")
            }

            AuthorizationResponse.Type.EMPTY, AuthorizationResponse.Type.UNKNOWN -> {
                diagnostics.state.lastAuthStatus = "Auth cancelled or unknown response"
                listener.onStatusChanged("Auth cancelled or unknown response")
                DebugLog.e(TAG, "Auth response was empty or unknown: ${response.state}")
            }

            else -> {
                diagnostics.state.lastAuthStatus = "Unhandled auth response: ${response.type}"
                DebugLog.e(TAG, "Unhandled auth response type: ${response.type}")
            }
        }
    }

    fun attemptConnect(authRequestCode: Int) {
        if (!diagnostics.isSpotifyInstalled()) {
            val message = "Spotify app not installed. Install and log in to Spotify then retry."
            diagnostics.state.lastConnectionStatus = message
            listener.onStatusChanged(message)
            DebugLog.e(TAG, message)
            return
        }
        connectionAttempt += 1
        diagnostics.state.lastConnectionStatus = "Connecting (attempt $connectionAttempt)"
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        DebugLog.i(TAG, "Connecting to SpotifyAppRemote attempt=$connectionAttempt redirect=$redirectUri")
        SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                diagnostics.state.lastConnectionStatus = "Connected"
                diagnostics.state.lastError = ""
                connectionAttempt = 0
                DebugLog.i(TAG, "Connected to SpotifyAppRemote")
                listener.onStatusChanged("Connected to Spotify")
                listener.onConnected(appRemote)
            }

            override fun onFailure(throwable: Throwable) {
                diagnostics.state.lastConnectionStatus = "Connection failed"
                diagnostics.state.lastError = DebugLog.formatThrowable(throwable)
                handleConnectionFailure(throwable, authRequestCode)
            }
        })
    }

    private fun handleConnectionFailure(throwable: Throwable, authRequestCode: Int) {
        val throwableSummary = DebugLog.formatThrowable(throwable)
        DebugLog.e(TAG, "SpotifyAppRemote connection failed", throwable)
        listener.onStatusChanged("Connect failed: $throwableSummary")

        val guidance = buildGuidanceMessage(throwable)
        if (guidance.isNotBlank()) {
            listener.onStatusChanged(guidance)
        }

        val shouldRetryAuth = throwableSummary.contains("authentication", ignoreCase = true)
                || throwableSummary.contains("AUTHENTICATION_SERVICE_UNAVAILABLE", ignoreCase = true)
        val hasRetriesLeft = connectionAttempt < retryConfig.maxAttempts
        if (shouldRetryAuth && hasRetriesLeft) {
            val delayIndex = min(connectionAttempt, retryConfig.backoffMillis.lastIndex)
            val delayMs = retryConfig.backoffMillis[delayIndex]
            DebugLog.i(TAG, "Auth-related failure detected, retrying after ${delayMs}ms (attempt $connectionAttempt)")
            activity.window.decorView.postDelayed({
                startAuthorization(authRequestCode)
            }, delayMs)
        }
    }

    private fun buildGuidanceMessage(throwable: Throwable): String {
        val message = throwable.message ?: "Unknown error"
        val lower = message.lowercase()
        val hints = mutableListOf<String>()
        if (diagnostics.isProbablyEmulator()) {
            hints += diagnostics.emulatorWarning()
        }
        if (!diagnostics.isSpotifyInstalled()) {
            hints += "Spotify app is not installed. Install it from the Play Store and log in."
        }
        if (lower.contains("authentication_service_unavailable")) {
            hints += "Spotify app may not be reachable or logged in. Ensure Spotify is running and you are logged in."
            hints += "On emulators, Play services and Spotify login often fail; prefer a physical device."
        }
        if (lower.contains("network") || lower.contains("internet") || lower.contains("connection")) {
            hints += "Check internet connectivity. Current: ${diagnostics.networkSummary()}"
        }
        if (lower.contains("redirect") || lower.contains("uri")) {
            hints += "Verify redirect URI $redirectUri matches the intent-filter."
        }
        if (lower.contains("client") || lower.contains("id")) {
            hints += "Confirm the clientId is correct for this package signature."
        }
        return hints.joinToString("\n")
    }

    companion object {
        private const val TAG = "SpotifyConnector"
    }
}
