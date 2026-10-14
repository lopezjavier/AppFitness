package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import kotlin.time.Instant

/** The signed-in user's weight entries. A failure carries an [io.github.lopezjavier.appfitness.domain.AppException]. */
interface BodyMeasurementRepository {
    /** The onboarding weight row: the earliest manual entry (by creation time), or null if none exists yet. */
    suspend fun getOnboardingWeight(): Result<BodyMeasurement?>

    /** Updates the onboarding weight row, or creates it when none exists. Idempotent: a retry never duplicates. */
    suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit>

    /** The most recent entry by measurement time, or null if there is none. */
    suspend fun getLatestWeight(): Result<BodyMeasurement?>
}
