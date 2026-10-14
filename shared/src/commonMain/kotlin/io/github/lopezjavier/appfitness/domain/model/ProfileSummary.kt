package io.github.lopezjavier.appfitness.domain.model

/** The slice of the user's profile the app shell needs: avatar and onboarding gate. */
data class ProfileSummary(val displayName: String?, val avatarUrl: String?, val onboardingCompleted: Boolean)
