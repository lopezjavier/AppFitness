package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.WeightEntry

interface ProfileRepository {
    /** The signed-in user's profile. A failure carries an [io.github.lopezjavier.appfitness.domain.AppException]. */
    suspend fun getOwnProfile(): Result<ProfileSummary>

    /** The biometric fields saved so far (any can be null while onboarding is unfinished). */
    suspend fun getBiometrics(): Result<SavedBiometrics>

    /** Saves the four biometric fields. Idempotent. */
    suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit>

    /** Sets `onboarding_completed = true` on the own profile. Idempotent. */
    suspend fun markOnboardingCompleted(): Result<Unit>

    /** Saves the display name only (onboarding, when Google gave none). The name is trimmed by the caller. */
    suspend fun updateDisplayName(name: String): Result<Unit>

    /** The weight goal in kg, or null when none is set. */
    suspend fun getTargetWeight(): Result<Double?>

    /** Sets the weight goal (20-400 kg) or clears it with null. */
    suspend fun setTargetWeight(kg: Double?): Result<Unit>

    /** True when the sign-in provider (Google) supplied a non-blank `full_name`. Reads the cached session. */
    fun hasProvidedFullName(): Boolean

    /**
     * Saves the name, biometrics and goals, and (when [weight] is not null) today's manual weight in one atomic
     * call (RPC `save_preferences`): all or nothing.
     */
    suspend fun savePreferences(
        displayName: String,
        biometrics: Biometrics,
        goals: NutritionGoals,
        weight: WeightEntry?,
        warmUpIncluded: Boolean,
    ): Result<Unit>
}
