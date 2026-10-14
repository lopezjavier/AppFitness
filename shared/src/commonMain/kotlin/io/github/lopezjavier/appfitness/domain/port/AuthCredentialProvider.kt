package io.github.lopezjavier.appfitness.domain.port

/** Result of asking the platform for a Google ID token. */
sealed interface GoogleCredentialResult {
    /** [rawNonce] is what Supabase verifies against the hashed nonce embedded in [idToken]. */
    data class Token(val idToken: String, val rawNonce: String) : GoogleCredentialResult

    data object Cancelled : GoogleCredentialResult

    data object NoNetwork : GoogleCredentialResult

    data class Failed(val cause: Throwable? = null) : GoogleCredentialResult
}

/** Opens the platform's Google account picker and returns an ID token. */
interface AuthCredentialProvider {
    suspend fun requestGoogleIdToken(): GoogleCredentialResult
}
