package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    suspend fun signInWithGoogle(idToken: String, rawNonce: String): SignInOutcome

    suspend fun signInWithPassword(credentials: TestLoginCredentials): SignInOutcome

    /** Never throws; always leaves the device without a local session. */
    suspend fun signOut()
}
