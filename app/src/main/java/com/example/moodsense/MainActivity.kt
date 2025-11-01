package com.example.moodsense

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class MainActivity : AppCompatActivity() {

    private val clientId = "4d864f8662144971ba0242cea48bfebf"
    private val redirectUri = "moodsense://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var accessToken: String? = null
    private var browserAuthInProgress = false

    private val requiredScopes = arrayOf(
        "user-read-playback-state",
        "user-modify-playback-state",
        "app-remote-control"
    )

    private val AUTH_REQUEST_CODE = 1337

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup button listeners
        findViewById<Button>(R.id.connect_button).setOnClickListener { startSpotifyAuthorization() }
        findViewById<Button>(R.id.play_button).setOnClickListener { spotifyAppRemote?.playerApi?.resume() }
        findViewById<Button>(R.id.pause_button).setOnClickListener { spotifyAppRemote?.playerApi?.pause() }
        findViewById<Button>(R.id.next_button).setOnClickListener { spotifyAppRemote?.playerApi?.skipNext() }
        findViewById<Button>(R.id.prev_button).setOnClickListener { spotifyAppRemote?.playerApi?.skipPrevious() }

        setPlaybackControlsEnabled(false)
    }

    override fun onStart() {
        super.onStart()
        if (spotifyAppRemote == null && accessToken != null) {
            connectToSpotifyAppRemote()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == AUTH_REQUEST_CODE) {
            intent?.let {
                handleAuthorizationResponse(AuthorizationClient.getResponse(resultCode, it))
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (browserAuthInProgress && intent?.data != null) {
            val response = AuthorizationResponse.fromUri(intent.data!!)
            browserAuthInProgress = false
            handleAuthorizationResponse(response)
        }
    }

    private fun startSpotifyAuthorization(forceBrowser: Boolean = false) {
        val request = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.TOKEN, redirectUri)
            .setScopes(requiredScopes)
            .setShowDialog(false)
            .build()

        if (forceBrowser) {
            openAuthorizationInBrowser(request)
            return
        }

        if (SpotifyAppRemote.isSpotifyInstalled(applicationContext)) {
            AuthorizationClient.openLoginActivity(this, AUTH_REQUEST_CODE, request)
        } else {
            openAuthorizationInBrowser(request)
            Toast.makeText(
                this,
                getString(R.string.install_spotify_prompt),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openAuthorizationInBrowser(request: AuthorizationRequest) {
        browserAuthInProgress = true
        try {
            AuthorizationClient.openLoginInBrowser(this, request)
        } catch (error: ActivityNotFoundException) {
            browserAuthInProgress = false
            Log.e("MainActivity", "No browser available for Spotify login", error)
            Toast.makeText(
                this,
                getString(R.string.browser_missing_error),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleAuthorizationResponse(response: AuthorizationResponse) {
        browserAuthInProgress = false
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                accessToken = response.accessToken
                Log.d("MainActivity", "Got access token")
                connectToSpotifyAppRemote()
            }
            AuthorizationResponse.Type.ERROR -> {
                Log.e("MainActivity", "Auth error: ${response.error}")
                if (response.error.equals("AUTHENTICATION_SERVICE_UNAVAILABLE", ignoreCase = true)) {
                    if (SpotifyAppRemote.isSpotifyInstalled(applicationContext)) {
                        Toast.makeText(
                            this,
                            getString(R.string.spotify_service_unavailable_retry),
                            Toast.LENGTH_LONG
                        ).show()
                        startSpotifyAuthorization(forceBrowser = true)
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.install_spotify_prompt),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.spotify_auth_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            else -> {
                Log.w("MainActivity", "Auth flow cancelled or unknown response type: ${response.type}")
                Toast.makeText(
                    this,
                    getString(R.string.spotify_auth_cancelled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun connectToSpotifyAppRemote() {
        if (!SpotifyAppRemote.isSpotifyInstalled(applicationContext)) {
            Toast.makeText(
                this,
                getString(R.string.install_spotify_prompt),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true) // Show auth view if necessary
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
                spotifyAppRemote = null
                setPlaybackControlsEnabled(false)

                if (throwable is CouldNotFindSpotifyApp) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.install_spotify_prompt),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.spotify_connection_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun connected() {
        spotifyAppRemote?.playerApi?.play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL")

        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            val track: Track? = playerState.track
            if (track != null) {
                findViewById<TextView>(R.id.track_name_textview).text = "Track: ${track.name} by ${track.artist.name}"
                Log.d("MainActivity", "${track.name} by ${track.artist.name}")
            }
        }

        setPlaybackControlsEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
        spotifyAppRemote = null
        setPlaybackControlsEnabled(false)
    }

    private fun setPlaybackControlsEnabled(isEnabled: Boolean) {
        findViewById<Button>(R.id.play_button).isEnabled = isEnabled
        findViewById<Button>(R.id.pause_button).isEnabled = isEnabled
        findViewById<Button>(R.id.next_button).isEnabled = isEnabled
        findViewById<Button>(R.id.prev_button).isEnabled = isEnabled
    }
}
