package com.example.moodsense

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.moodsense.spotify.DebugLog
import com.example.moodsense.spotify.SpotifyConnector
import com.example.moodsense.spotify.SpotifyDiagnostics
import com.example.moodsense.spotify.auth.SpotifyAuthManager
import com.example.moodsense.spotify.auth.SpotifyTokenRepository
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.Track
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), SpotifyConnector.Listener {

    private val clientId = "4d864f8662144971ba0242cea48bfebf"
    private val redirectUri = "moodsense://callback"
    private val authScopes = listOf(
        "app-remote-control",
        "user-read-playback-state",
        "user-modify-playback-state",
        "user-read-currently-playing"
    )

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private lateinit var statusTextView: TextView
    private lateinit var diagnosticsTextView: TextView
    private lateinit var diagnosticsContainer: LinearLayout
    private lateinit var diagnosticsToggle: Button

    private lateinit var diagnostics: SpotifyDiagnostics
    private lateinit var connector: SpotifyConnector
    private lateinit var tokenRepository: SpotifyTokenRepository
    private lateinit var authManager: SpotifyAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        diagnostics = SpotifyDiagnostics(applicationContext)
        tokenRepository = SpotifyTokenRepository(applicationContext)
        authManager = SpotifyAuthManager(
            activity = this,
            clientId = clientId,
            redirectUri = redirectUri,
            tokenRepository = tokenRepository,
            diagnostics = diagnostics,
            scopes = authScopes
        )
        connector = SpotifyConnector(this, clientId, redirectUri, diagnostics, this)

        statusTextView = findViewById(R.id.status_textview)
        diagnosticsTextView = findViewById(R.id.diagnostics_textview)
        diagnosticsContainer = findViewById(R.id.diagnostics_container)
        diagnosticsToggle = findViewById(R.id.toggle_diagnostics_button)

        setupButtons()
        setPlaybackControlsEnabled(false)
        updateDiagnosticsText()

        if (diagnostics.isProbablyEmulator()) {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = diagnostics.emulatorWarning()
        }

        lifecycleScope.launch {
            trySilentConnection()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent?.data)
        DebugLog.i(TAG, "onNewIntent called with data: ${intent?.data}")
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyAppRemote = null
        setPlaybackControlsEnabled(false)
    }

    override fun onStatusChanged(status: String) {
        runOnUiThread {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = status
            updateDiagnosticsText()
        }
    }

    override fun onConnected(appRemote: SpotifyAppRemote) {
        spotifyAppRemote = appRemote
        runOnUiThread {
            connected()
            updateDiagnosticsText()
        }
    }

    override fun onAuthenticationFailed() {
        runOnUiThread {
            diagnostics.state.lastTokenStatus = "Token rejected; please sign in again"
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = "Spotify session expired. Please reconnect."
            connectorMessage("Requesting new authentication...")
            authManager.clearTokens()
            authManager.startAuthorization()
            updateDiagnosticsText()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.connect_button).setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = "Checking Spotify session..."
            if (diagnostics.isProbablyEmulator()) {
                Toast.makeText(this, diagnostics.emulatorWarning(), Toast.LENGTH_LONG).show()
            }
            lifecycleScope.launch { beginConnectionFlow() }
        }
        findViewById<Button>(R.id.play_button).setOnClickListener { spotifyAppRemote?.playerApi?.resume() }
        findViewById<Button>(R.id.pause_button).setOnClickListener { spotifyAppRemote?.playerApi?.pause() }
        findViewById<Button>(R.id.next_button).setOnClickListener { spotifyAppRemote?.playerApi?.skipNext() }
        findViewById<Button>(R.id.prev_button).setOnClickListener { spotifyAppRemote?.playerApi?.skipPrevious() }

        diagnosticsToggle.setOnClickListener {
            diagnosticsContainer.visibility = if (diagnosticsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            diagnosticsToggle.text = if (diagnosticsContainer.visibility == View.VISIBLE) "Hide diagnostics" else "Show diagnostics"
            updateDiagnosticsText()
        }
        findViewById<Button>(R.id.copy_diagnostics_button).setOnClickListener { copyDiagnosticsToClipboard() }
    }

    private fun connected() {
        findViewById<Button>(R.id.connect_button).visibility = View.GONE
        findViewById<LinearLayout>(R.id.player_controls).visibility = View.VISIBLE
        findViewById<TextView>(R.id.track_name_textview).visibility = View.VISIBLE

        spotifyAppRemote?.playerApi?.play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL")
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            val track: Track? = playerState.track
            if (track != null) {
                findViewById<TextView>(R.id.track_name_textview).text = "Track: ${track.name} by ${track.artist.name}"
                DebugLog.i(TAG, "${track.name} by ${track.artist.name}")
            }
        }
        setPlaybackControlsEnabled(true)
    }

    private fun setPlaybackControlsEnabled(isEnabled: Boolean) {
        findViewById<Button>(R.id.play_button).isEnabled = isEnabled
        findViewById<Button>(R.id.pause_button).isEnabled = isEnabled
        findViewById<Button>(R.id.next_button).isEnabled = isEnabled
        findViewById<Button>(R.id.prev_button).isEnabled = isEnabled
    }

    private fun copyDiagnosticsToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val diagnosticsText = diagnostics.formatDiagnostics(clientId, redirectUri)
        val clip = ClipData.newPlainText("MoodSense diagnostics", diagnosticsText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun updateDiagnosticsText() {
        diagnosticsTextView.text = diagnostics.formatDiagnostics(clientId, redirectUri)
    }

    private suspend fun beginConnectionFlow() {
        diagnostics.state.lastTokenStatus = "Checking stored token"
        val refreshedToken = authManager.refreshTokensIfNeeded()
        refreshedToken.onSuccess { token ->
            diagnostics.state.lastTokenStatus = "Valid token (expires in ${token.expiresInSeconds()}s)"
            connector.connect(token.accessToken)
        }.onFailure {
            diagnostics.state.lastTokenStatus = "Login required"
            connectorMessage("Opening Spotify login...")
            authManager.startAuthorization()
        }
        updateDiagnosticsText()
    }

    private suspend fun trySilentConnection() {
        val tokenResult = authManager.refreshTokensIfNeeded()
        tokenResult.onSuccess { token ->
            diagnostics.state.lastTokenStatus = "Valid token (expires in ${token.expiresInSeconds()}s)"
            connector.connect(token.accessToken)
        }.onFailure {
            diagnostics.state.lastTokenStatus = "No active session"
        }
        updateDiagnosticsText()
    }

    private fun handleAuthRedirect(uri: Uri?) {
        when (val redirectResult = authManager.handleRedirect(uri)) {
            is SpotifyAuthManager.RedirectResult.Ignored -> return
            is SpotifyAuthManager.RedirectResult.Error -> {
                diagnostics.state.lastAuthStatus = redirectResult.message
                connectorMessage(redirectResult.message)
                updateDiagnosticsText()
            }
            is SpotifyAuthManager.RedirectResult.Success -> {
                lifecycleScope.launch {
                    connectorMessage("Exchanging authorization code...")
                    val exchange = authManager.exchangeCodeForTokens(redirectResult.code)
                    exchange.onSuccess { result ->
                        diagnostics.state.lastTokenStatus = tokenRepository.describe()
                        connector.connect(result.tokens.accessToken)
                    }.onFailure { error ->
                        diagnostics.state.lastTokenStatus = "Token exchange failed: ${error.message}"
                        connectorMessage("Token exchange failed: ${error.message}")
                    }
                    updateDiagnosticsText()
                }
            }
        }
    }

    private fun connectorMessage(message: String) {
        runOnUiThread {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = message
            updateDiagnosticsText()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
