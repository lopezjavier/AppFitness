package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.lopezjavier.appfitness.domain.model.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthStateMappingTest {
    private fun session(user: UserInfo?) = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = user,
    )

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
    fun authenticatedSessionWithoutUserObject_usesTheCurrentUserIdFallback() {
        val status = SessionStatus.Authenticated(session(user = null))

        assertEquals(AuthState.SignedIn("user-1"), status.toAuthState { "user-1" })
        assertEquals(AuthState.SignedOut, status.toAuthState { null })
    }

    @Test
    fun authenticatedSessionWithUser_usesItsId() {
        val user = UserInfo(id = "from-session", aud = "authenticated")
        val status = SessionStatus.Authenticated(session(user))

        assertEquals(AuthState.SignedIn("from-session"), status.toAuthState { "other" })
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
