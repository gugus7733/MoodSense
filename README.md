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
2. Complete the Spotify login flow (implicit token grant). After a successful login, MoodSense connects to the Spotify App Remote SDK. Playback controls become enabled and you can play, pause, skip, and go to the previous track. The currently playing track name appears at the top of the screen.
3. Toggle **Show diagnostics** to inspect the current environment (emulator detection, Spotify install status, network, redirect URI, last errors) and the live debug log. Tap **Copy diagnostics** to share the collected information.
4. The app automatically disables playback controls if the connection drops or when the activity stops.

## Troubleshooting and diagnostics

- **Running on an emulator:** The diagnostics panel will state if the device looks like an emulator. Spotify App Remote frequently fails on emulators because the Spotify app or Play services are missing. Prefer testing on a physical device with Spotify installed and logged in.
- **`AUTHENTICATION_SERVICE_UNAVAILABLE` or repeated authentication failures:** The diagnostics panel shows the full error chain and suggests likely causes. Verify Spotify is installed and logged in, the network is available, and the redirect URI `moodsense://callback` matches the manifest intent-filter.
- **Spotify not installed:** The connect flow will stop early and display guidance. Install the Spotify app from the Play Store and log in before retrying.
- **Copying diagnostics:** Use the **Copy diagnostics** button to capture the device info, network summary, redirect URI, auth/connect status, and the most recent log lines for further debugging.

