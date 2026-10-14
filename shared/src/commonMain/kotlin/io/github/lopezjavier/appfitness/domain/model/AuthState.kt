package io.github.lopezjavier.appfitness.domain.model

/** Whether and as whom the user is signed in. */
sealed interface AuthState {
    /** The stored session is still being read or refreshed. */
    data object Loading : AuthState

    data class SignedIn(val userId: String) : AuthState

    data object SignedOut : AuthState
}
