package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import kotlin.time.Instant

/** Used when Supabase is not configured: nothing can be read or stored. */
class UnconfiguredDiscomfortRepository : DiscomfortRepository {
    override suspend fun log(exerciseId: String, note: String?, severe: Boolean): Result<Discomfort> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>> =
        Result.failure(AppException(AppError.Unknown))
}
