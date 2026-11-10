package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.BlockPauseDto
import io.github.lopezjavier.appfitness.data.remote.CancelDeloadParams
import io.github.lopezjavier.appfitness.data.remote.EvaluateDeloadParams
import io.github.lopezjavier.appfitness.data.remote.EvaluateDeloadResultDto
import io.github.lopezjavier.appfitness.data.remote.PlanWeekDto
import io.github.lopezjavier.appfitness.data.remote.PlanWeeksParams
import io.github.lopezjavier.appfitness.data.remote.SetBlockEndParams
import io.github.lopezjavier.appfitness.data.remote.toCoachChange
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement

private const val NOT_FOUND_CODE = "P0002"

/** Weeks and deload through the `coach_plan_weeks` and `*_coach_deload` RPCs (evaluate, request, cancel). */
class SupabaseCoachWeeksRepository(private val auth: Auth, private val postgrest: Postgrest) : CoachWeeksRepository {
    override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> = auth.ownRow {
        postgrest.rpc("coach_plan_weeks", PlanWeeksParams(planId, today))
            .decodeList<PlanWeekDto>()
            .mapNotNull { it.toDomain() }
            .sortedBy { it.week }
    }

    override suspend fun blockPause(planId: String, today: LocalDate): Result<BlockPause> = auth.ownRow {
        postgrest.rpc("coach_block_pause", PlanWeeksParams(planId, today)).decodeAs<BlockPauseDto>().toDomain()
    }

    override suspend fun setBlockEnd(planId: String, lastDay: LocalDate, today: LocalDate): Result<BlockPause> =
        auth.ownRow {
            postgrest.rpc("set_coach_block_end", SetBlockEndParams(planId, lastDay, today))
                .decodeAs<BlockPauseDto>()
                .toDomain()
        }

    override suspend fun evaluateDeload(today: LocalDate): Result<CoachChange?> = auth.ownRow {
        postgrest.rpc("evaluate_coach_deload", EvaluateDeloadParams(today))
            .decodeAs<EvaluateDeloadResultDto>()
            .toChange()
    }

    override suspend fun requestDeload(): Result<CoachChange> = auth.ownRow {
        postgrest.rpc("request_coach_deload").decodeAs<JsonElement>().toCoachChange()
            ?: throw AppException(AppError.Unknown)
    }.mapDeloadErrors()

    override suspend fun cancelDeload(changeId: String): Result<CoachChange> = auth.ownRow {
        postgrest.rpc("cancel_coach_deload", CancelDeloadParams(changeId)).decodeAs<JsonElement>().toCoachChange()
            ?: throw AppException(AppError.Unknown)
    }.mapDeloadErrors()

    /** P0001 carries the server code in the message; P0002 is a foreign or unknown change id. */
    private fun <T> Result<T>.mapDeloadErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        val text = "${cause.message.orEmpty()} ${cause.description.orEmpty()}"
        when {
            text.contains("coach_change_not_undoable") ->
                throw CoachChangeException(CoachChangeFailure.NotUndoable, cause)

            text.contains("coach_deload_") -> throw CoachChangeException(CoachChangeFailure.DeloadNotAvailable, cause)

            cause.code == NOT_FOUND_CODE -> throw AppException(AppError.NotFound, cause)

            else -> throw failure
        }
    }
}
