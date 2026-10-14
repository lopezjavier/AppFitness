package io.github.lopezjavier.appfitness.platform

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val NONCE_BYTES = 32

/**
 * Raw nonce for one Google sign-in: 32 secure random bytes, URL-safe Base64 without padding.
 * Google embeds its SHA-256 ([sha256Hex]); Supabase verifies the raw value.
 */
fun generateRawNonce(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/** Lowercase hexadecimal SHA-256 of [input] (UTF-8). */
fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") {
        "%02x".format(it)
    }
