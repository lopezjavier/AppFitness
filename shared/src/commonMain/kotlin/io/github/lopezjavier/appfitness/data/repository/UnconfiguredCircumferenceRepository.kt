package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceEntry
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import kotlin.time.Instant

/** Circumference counterpart of [UnconfiguredBodyMeasurementRepository]: nothing can be read or saved. */
class UnconfiguredCircumferenceRepository : CircumferenceRepository {
    override suspend fun getAll(): Result<List<BodyCircumference>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun addAll(entries: List<CircumferenceEntry>, measuredAt: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun update(id: String, valueCm: Double, measuredAt: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun delete(id: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
