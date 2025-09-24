package com.example.moodsense

import android.os.Bundle
import com.example.moodsense.ui.theme.MoodSenseTheme

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.ComponentActivity // Changed
import androidx.activity.compose.setContent

import android.util.Log;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;

import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Track;

import android.content.Intent
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

private const val REQUEST_CODE_SPOTIFY_AUTH = 1337 // Or any unique request code

class MainActivity : AppCompatActivity() {

    private val clientId = "4d864f8662144971ba0242cea48bfebf"
    private val redirectUri = "moodsense://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        // Try to connect first. If it fails due to authorization, then authenticate.
        connectToAppRemote()
    }

    private fun connectToAppRemote() { // Extracted to a new function for clarity
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true) // Keep this, it might help in some scenarios
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                connected() // Your function to play music
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", "Connection failed: " + throwable.message, throwable)
                if (throwable is com.spotify.android.appremote.api.error.UserNotAuthorizedException ||
                    throwable is com.spotify.android.appremote.api.error.AuthenticationFailedException) { // More specific check
                    Log.d("MainActivity", "User not authorized or auth failed, attempting to authenticate...")
                    // NOW WE CALL YOUR AUTHENTICATION FUNCTION
                    authenticateWithSpotify()
                } else {
                    // Handle other errors (e.g., Spotify not installed, network issues)
                    Log.e("MainActivity", "Other connection error: ", throwable)
                }
            }
        })
    }

    private fun connected() {
        spotifyAppRemote?.let {
            // Play a playlist
            val playlistURI = "spotify:playlist:37i9dQZF1DX2sUQwD7tbmL"
            it.playerApi.play(playlistURI)
            // Subscribe to PlayerState
            it.playerApi.subscribeToPlayerState().setEventCallback {
                val track: Track = it.track
                Log.d("MainActivity", track.name + " by " + track.artist.name)
            }
        }

    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }

    }

    fun authenticateWithSpotify() {
        val builder = AuthorizationRequest.Builder(
            clientId,
            AuthorizationResponse.Type.TOKEN, // Or .CODE if you're using a backend
            redirectUri
        )
        builder.setScopes(arrayOf("user-read-private", "playlist-read-private", "app-remote-control", "streaming")) // Request necessary scopes
        // Add other scopes like "user-modify-playback-state", "user-read-playback-state" as needed
        val request = builder.build()

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE_SPOTIFY_AUTH, request)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        // Check if this is the result from the Spotify Login Activity
        if (requestCode == REQUEST_CODE_SPOTIFY_AUTH) { // REQUEST_CODE_SPOTIFY_AUTH is 1337 in your code
            val response = AuthorizationClient.getResponse(resultCode, intent)

            when (response.type) {
                // Response was successful and contains an auth token
                AuthorizationResponse.Type.TOKEN -> {
                    val accessToken = response.accessToken
                    Log.d("MainActivity", "Successfully got auth token: $accessToken")
                    // IMPORTANT: The Spotify SDKs (Auth and App Remote) handle token storage
                    // and usage somewhat internally after this point for App Remote.
                    // Now that we have a token (or at least completed the auth flow),
                    // try connecting to App Remote again.
                    Log.d("MainActivity", "Auth successful, trying to connect to App Remote again.")
                    connectToAppRemote() // Call your connection function again
                }

                // Auth flow returned an error
                AuthorizationResponse.Type.ERROR -> {
                    Log.e("MainActivity", "Auth error: " + response.error)
                    // You might want to show a message to the user here
                }

                // Most other cases (cancelled, etc.) can be treated as a failure
                else -> {
                    Log.d("MainActivity", "Auth result: " + response.type)
                    // Handle other cases if needed
                }
            }
        }
    }
}

