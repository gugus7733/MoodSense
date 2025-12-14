package com.example.moodsense.spotify.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

object PkceUtil {
    private const val VERIFIER_BYTE_SIZE = 64
    private val secureRandom = SecureRandom()

    fun generateCodeVerifier(): String {
        val code = ByteArray(VERIFIER_BYTE_SIZE)
        secureRandom.nextBytes(code)
        return Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes, 0, bytes.size)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun generateState(): String {
        val stateBytes = ByteArray(32)
        secureRandom.nextBytes(stateBytes)
        return Base64.encodeToString(stateBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
