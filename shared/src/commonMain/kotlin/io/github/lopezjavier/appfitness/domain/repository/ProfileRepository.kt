package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics

interface ProfileRepository {
    /** The signed-in user's profile. A failure carries an [io.github.lopezjavier.appfitness.domain.AppException]. */
    suspend fun getOwnProfile(): Result<ProfileSummary>

    /** The biometric fields saved so far (any can be null while onboarding is unfinished). */
    suspend fun getBiometrics(): Result<SavedBiometrics>

    /** Saves the four biometric fields. Idempotent. */
    suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit>
}
