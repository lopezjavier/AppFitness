package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthStateMappingTest {
    @Test
    fun initializing_isLoading() {
        assertEquals(AuthState.Loading, SessionStatus.Initializing.toAuthState { null })
    }

    @Test
    fun notAuthenticated_isSignedOut() {
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = true).toAuthState { null })
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = false).toAuthState { "u" })
    }

    @Test
    fun refreshFailure_keepsTheUserSignedIn() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("offline")))

        assertEquals(AuthState.SignedIn("user-1"), status.toAuthState { "user-1" })
    }

    @Test
    fun refreshFailure_withoutAnyKnownUser_isSignedOut() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("offline")))

        assertEquals(AuthState.SignedOut, status.toAuthState { null })
    }
}
