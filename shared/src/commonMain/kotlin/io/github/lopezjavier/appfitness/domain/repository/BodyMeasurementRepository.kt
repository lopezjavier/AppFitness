package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import kotlin.time.Instant

/** The signed-in user's weight entries. A failure carries an [io.github.lopezjavier.appfitness.domain.AppException]. */
interface BodyMeasurementRepository {
    /** The onboarding weight row: the earliest manual entry (by creation time), or null if none exists yet. */
    suspend fun getOnboardingWeight(): Result<BodyMeasurement?>

    /** Updates the onboarding weight row, or creates it when none exists. Idempotent: a retry never duplicates. */
    suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit>

    /** The most recent entry by measurement time, or null if there is none. */
    suspend fun getLatestWeight(): Result<BodyMeasurement?>

    /** Entries with `from <= measuredAt < toExclusive` of any source, newest first. */
    suspend fun getMeasurements(from: Instant, toExclusive: Instant): Result<List<BodyMeasurement>>

    /**
     * Stores [save] atomically: one manual weight per local day, and an edit never moves the entry to another day.
     * Fails with `Validation` for out-of-range values or a day mismatch and `NotFound` for a row that is not the
     * caller's manual one.
     */
    suspend fun saveManualWeight(save: ManualWeightSave): Result<Unit>

    /** Deletes entry [id] of any source; an id that is not the caller's deletes nothing and still succeeds. */
    suspend fun deleteMeasurement(id: String): Result<Unit>
}
