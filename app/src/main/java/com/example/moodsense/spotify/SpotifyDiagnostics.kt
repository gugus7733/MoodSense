package com.example.moodsense.spotify

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.spotify.android.appremote.api.SpotifyAppRemote

class SpotifyDiagnostics(private val context: Context) {
    data class DiagnosticsState(
        var lastAuthStatus: String = "Not started",
        var lastConnectionStatus: String = "Not connected",
        var lastError: String = "",
        var receivedRedirect: Boolean = false,
        var lastTokenStatus: String = "No token"
    )

    val state: DiagnosticsState = DiagnosticsState()

    fun markRedirectReceived(intentDescription: String?) {
        state.receivedRedirect = true
        DebugLog.i(TAG, "Redirect intent received: ${intentDescription ?: "<no description>"}")
    }

    fun isSpotifyInstalled(): Boolean = SpotifyAppRemote.isSpotifyInstalled(context)

    fun installedSpotifyDetails(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(SPOTIFY_PACKAGE, 0)
            "installed (version=${packageInfo.versionName ?: "unknown"}, code=${packageInfo.longVersionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "not installed"
        } catch (e: Exception) {
            DebugLog.e(TAG, "Unable to check Spotify package", e)
            "unknown (error: ${e.message})"
        }
    }

    fun networkSummary(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return "offline (no active network)"
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return "offline (no capabilities)"
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val transports = mutableListOf<String>()
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transports.add("cellular")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("ethernet")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) transports.add("bluetooth")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transports.add("vpn")
            val transportLabel = if (transports.isEmpty()) "unknown" else transports.joinToString(",")
            if (hasInternet) "online ($transportLabel)" else "connected ($transportLabel) without internet capability"
        } catch (e: Exception) {
            DebugLog.e(TAG, "Error while detecting network", e)
            "unknown (error: ${e.message})"
        }
    }

    fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()

        val emulatorIndicators = listOf(
            fingerprint.contains("generic"),
            fingerprint.contains("emulator"),
            model.contains("emulator"),
            model.contains("android sdk built for x86"),
            manufacturer.contains("genymotion"),
            brand.startsWith("generic") && device.startsWith("generic"),
            product.contains("sdk"),
            product.contains("emulator")
        )
        return emulatorIndicators.any { it }
    }

    fun emulatorWarning(): String {
        return "Spotify App Remote often fails on emulators. For reliable testing, use a physical device with Spotify installed and logged in."
    }

    fun formatDiagnostics(clientId: String, redirectUri: String): String {
        val lines = mutableListOf<String>()
        lines += "Diagnostics snapshot"
        lines += "Device: ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})"
        lines += "Probably emulator: ${isProbablyEmulator()}"
        lines += "Spotify installed: ${isSpotifyInstalled()} (${installedSpotifyDetails()})"
        lines += "Network: ${networkSummary()}"
        lines += "Client ID: $clientId"
        lines += "Redirect URI: $redirectUri"
        lines += "Received redirect intent: ${state.receivedRedirect}"
        lines += "Token status: ${state.lastTokenStatus}"
        lines += "Auth status: ${state.lastAuthStatus}"
        lines += "Connection status: ${state.lastConnectionStatus}"
        if (state.lastError.isNotBlank()) {
            lines += "Last error: ${state.lastError}"
        }
        lines += "--- Recent logs ---"
        lines.addAll(DebugLog.recentLogLines())
        return lines.joinToString(separator = "\n")
    }

    companion object {
        private const val TAG = "SpotifyDiagnostics"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
