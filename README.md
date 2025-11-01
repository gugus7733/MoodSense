# MoodSense Android App

MoodSense is an Android application that connects to Spotify so you can control playback directly from your device. The app uses the Spotify App Remote SDK to issue playback commands and displays information about the current track.

## Spotify developer configuration

1. Create a Spotify developer application at [https://developer.spotify.com/dashboard](https://developer.spotify.com/dashboard).
2. Set the package name to `com.example.moodsense` and add your app's SHA-1 signature.
3. Add a redirect URI equal to `moodsense://callback`.
4. Copy the **Client ID** from your Spotify dashboard and update the `clientId` constant in `app/src/main/java/com/example/moodsense/MainActivity.kt`.
5. Install the Spotify app on the device where MoodSense will run and make sure you are signed in.

## Building and running

1. Ensure the Spotify SDK `.aar` files (`spotify-app-remote-release-0.8.0.aar` and `spotify-auth-release-2.1.0.aar`) are available under `app/libs/`. If they are not present, download them from the [Spotify Android SDK releases](https://github.com/spotify/android-sdk/releases) page and place them in that directory.
2. Sync the Gradle project in Android Studio.
3. Build and run the app on a device with the Spotify app installed and connected to the internet.

## Using the app

1. Launch MoodSense and tap **Connect to Spotify**.
2. If the Spotify app is installed, the Spotify login screen appears. Complete the login flow. If the app is not installed or cannot be reached, the app falls back to the browser-based authentication flow.
3. After a successful login, MoodSense connects to the Spotify App Remote SDK. Playback controls become enabled and you can play, pause, skip, and go to the previous track. The currently playing track name appears at the top of the screen.
4. The app automatically disables playback controls if the connection drops or when the activity stops.

## Troubleshooting

- **`AUTHENTICATION_SERVICE_UNAVAILABLE` error**: MoodSense now detects this condition and retries the authentication using the browser flow. Make sure the Spotify service is reachable (internet connection) and that the Spotify app is installed if you want to control playback on the device.
- **Cannot connect to Spotify**: Ensure the Spotify app is running and that you are logged in with a Spotify Premium account, which is required for remote playback control.
- **Login opens in the browser**: This is normal when the Spotify app is not available. After completing login, you are redirected back to MoodSense automatically.

