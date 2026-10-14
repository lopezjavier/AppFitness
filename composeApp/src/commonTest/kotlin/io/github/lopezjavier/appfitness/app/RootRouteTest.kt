package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals

class RootRouteTest {
    private val signedIn = AuthState.SignedIn("user-1")

    @Test
    fun signedOut_isWelcome() {
        assertEquals(RootRoute.Welcome, resolveRootRoute(AuthState.SignedOut, ProfileLoad.Loading))
    }

    @Test
    fun firstSignIn_notOnboarded_goesToOnboarding() {
        assertEquals(RootRoute.Onboarding, resolveRootRoute(signedIn, ProfileLoad.Loaded(onboardingCompleted = false)))
    }

    @Test
    fun persistentSession_onboarded_goesToMain() {
        assertEquals(RootRoute.Main, resolveRootRoute(signedIn, ProfileLoad.Loaded(onboardingCompleted = true)))
    }

    @Test
    fun authLoading_isLoading() {
        assertEquals(RootRoute.Loading, resolveRootRoute(AuthState.Loading, ProfileLoad.Loading))
    }

    @Test
    fun signedInWhileProfileLoads_isLoadingNeverWelcome() {
        assertEquals(RootRoute.Loading, resolveRootRoute(signedIn, ProfileLoad.Loading))
    }

    @Test
    fun signedInWithFailedProfile_isProfileError() {
        assertEquals(RootRoute.ProfileError, resolveRootRoute(signedIn, ProfileLoad.Failed))
    }
}
