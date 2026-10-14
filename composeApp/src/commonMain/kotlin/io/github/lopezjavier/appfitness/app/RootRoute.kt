package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.AuthState

/** What the root of the app shows, decided from the session and the profile (US-05). */
sealed interface RootRoute {
    /** Session or profile still resolving: a skeleton, never Welcome. */
    data object Loading : RootRoute

    data object Welcome : RootRoute

    /** Signed in but onboarding is not completed (a stub destination until the onboarding epic). */
    data object Onboarding : RootRoute

    /** Signed in and onboarded: the tabbed shell starting on Diario. */
    data object Main : RootRoute

    /** Signed in but the profile could not be read. */
    data object ProfileError : RootRoute
}

/** State of the profile read that follows a sign-in. */
sealed interface ProfileLoad {
    data object Loading : ProfileLoad

    data class Loaded(val onboardingCompleted: Boolean) : ProfileLoad

    data object Failed : ProfileLoad
}

/** Pure routing rule. A signed-in user whose profile is not known yet is never sent to Welcome. */
fun resolveRootRoute(auth: AuthState, profile: ProfileLoad): RootRoute = when (auth) {
    AuthState.Loading -> RootRoute.Loading

    AuthState.SignedOut -> RootRoute.Welcome

    is AuthState.SignedIn ->
        when (profile) {
            ProfileLoad.Loading -> RootRoute.Loading
            ProfileLoad.Failed -> RootRoute.ProfileError
            is ProfileLoad.Loaded -> if (profile.onboardingCompleted) RootRoute.Main else RootRoute.Onboarding
        }
}
