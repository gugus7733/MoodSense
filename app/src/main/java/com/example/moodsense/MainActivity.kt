package com.example.moodsense

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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

    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.status_textview)

        // Setup button listeners
        findViewById<Button>(R.id.connect_button).setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = "Connecting..."
            val builder = AuthorizationRequest.Builder(clientId, AuthorizationResponse.Type.CODE, redirectUri)
            builder.setScopes(arrayOf("streaming", "user-read-playback-state", "user-modify-playback-state"))
            val request = builder.build()
            AuthorizationClient.openLoginActivity(this, AUTH_REQUEST_CODE, request)
        }
        findViewById<Button>(R.id.play_button).setOnClickListener { spotifyAppRemote?.playerApi?.resume() }
        findViewById<Button>(R.id.pause_button).setOnClickListener { spotifyAppRemote?.playerApi?.pause() }
        findViewById<Button>(R.id.next_button).setOnClickListener { spotifyAppRemote?.playerApi?.skipNext() }
        findViewById<Button>(R.id.prev_button).setOnClickListener { spotifyAppRemote?.playerApi?.skipPrevious() }

        setPlaybackControlsEnabled(false)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == AUTH_REQUEST_CODE) {
            intent?.let {
                handleAuthorizationResponse(AuthorizationClient.getResponse(resultCode, it))
            }
        }
    }

            when (response.type) {
                AuthorizationResponse.Type.CODE -> {
                    val code = response.code
                    Log.d("MainActivity", "Got authorization code: $code")
                    connectToSpotifyAppRemote()
                }
                AuthorizationResponse.Type.ERROR -> {
                    Log.e("MainActivity", "Auth error: " + response.error)
                    statusTextView.text = "Auth error: ${response.error}"
                }
                else -> {
                    Log.w("MainActivity", "Auth flow cancelled or unknown response type: ${response.type}")
                    statusTextView.text = "Auth flow cancelled"
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
                statusTextView.text = "Connected!"
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
                statusTextView.text = "Connection failed: ${throwable.message}"
            }
        })
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
