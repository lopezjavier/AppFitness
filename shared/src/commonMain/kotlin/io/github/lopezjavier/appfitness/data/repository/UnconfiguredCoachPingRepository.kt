package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.repository.CoachPingRepository

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachPingRepository : CoachPingRepository {
    override suspend fun ping(): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
