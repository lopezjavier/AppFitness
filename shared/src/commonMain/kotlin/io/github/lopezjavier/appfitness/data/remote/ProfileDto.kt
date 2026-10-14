package io.github.lopezjavier.appfitness.data.remote

import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row of `public.profiles` limited to the columns the shell reads. */
@Serializable
internal data class ProfileDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false,
) {
    fun toDomain(): ProfileSummary = ProfileSummary(
        displayName = displayName,
        avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        onboardingCompleted = onboardingCompleted,
    )

    companion object {
        const val COLUMNS = "display_name,avatar_url,onboarding_completed"
    }
}
