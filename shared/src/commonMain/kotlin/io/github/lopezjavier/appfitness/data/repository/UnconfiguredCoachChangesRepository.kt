package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachChangesRepository : CoachChangesRepository {
    override suspend fun apply(sessionId: String): Result<List<CoachChange>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun list(planId: String?, statuses: Set<CoachChangeStatus>): Result<List<CoachChange>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun undo(id: String): Result<UndoResult> = Result.failure(AppException(AppError.Unknown))

    override suspend fun decide(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult> =
        Result.failure(AppException(AppError.Unknown))
}
