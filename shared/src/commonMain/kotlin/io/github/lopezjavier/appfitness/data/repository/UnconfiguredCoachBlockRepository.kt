package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockEdit
import io.github.lopezjavier.appfitness.domain.model.BlockSaved
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.SplitOption
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import kotlinx.datetime.LocalDate

/** Used when Supabase is not configured: every call fails. */
class UnconfiguredCoachBlockRepository : CoachBlockRepository {
    override suspend fun requestSplitOptions(today: LocalDate): Result<List<SplitOption>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun requestBlock(planId: String, today: LocalDate, split: SplitOption): Result<CoachBlock> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun currentBlock(planId: String): Result<CoachBlock?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun saveBlock(planId: String, edits: List<BlockEdit>): Result<BlockSaved> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun removeBlock(planId: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
