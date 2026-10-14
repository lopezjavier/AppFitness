package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException

/**
 * [AuthRepository] over supabase-kt. A refresh failure (offline cold start) keeps the user signed in
 * (ADR-01); only an explicit absence of a session is [AuthState.SignedOut].
 */
class SupabaseAuthRepository(private val auth: Auth) : AuthRepository {
    override val authState: Flow<AuthState> =
        auth.sessionStatus
            .map { status -> status.toAuthState { auth.currentUserOrNull()?.id } }
            .distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): SignInOutcome = attempt {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun signInWithPassword(credentials: TestLoginCredentials): SignInOutcome = attempt {
        auth.signInWith(Email) {
            email = credentials.email
            password = credentials.password
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun signOut() {
        try {
            auth.signOut(SignOutScope.LOCAL)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // The server could not be reached: the device must still end up without a session.
            runCatching { auth.clearSession() }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun attempt(block: suspend () -> Unit): SignInOutcome = try {
        block()
        SignInOutcome.Success
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        failure.toSignInOutcome()
    }
}

internal fun SessionStatus.toAuthState(currentUserId: () -> String?): AuthState = when (this) {
    is SessionStatus.Initializing -> AuthState.Loading
    is SessionStatus.Authenticated -> session.user?.id?.let(AuthState::SignedIn) ?: AuthState.SignedOut
    is SessionStatus.RefreshFailure -> currentUserId()?.let(AuthState::SignedIn) ?: AuthState.SignedOut
    is SessionStatus.NotAuthenticated -> AuthState.SignedOut
}

internal fun Throwable.toSignInOutcome(): SignInOutcome = when (this) {
    is RestException -> SignInOutcome.Failed
    is HttpRequestException, is HttpRequestTimeoutException, is IOException -> SignInOutcome.NoNetwork
    else -> SignInOutcome.Failed
}
