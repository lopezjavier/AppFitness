package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ApplyCoachProgressionParams
import io.github.lopezjavier.appfitness.data.remote.DecideProgressionFlagParams
import io.github.lopezjavier.appfitness.data.remote.UndoCoachChangeParams
import io.github.lopezjavier.appfitness.data.remote.UndoResultDto
import io.github.lopezjavier.appfitness.data.remote.coachChangesOf
import io.github.lopezjavier.appfitness.data.remote.flagDecisionOf
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.FlagDecisionResult
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.model.UndoResult
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository
import kotlinx.serialization.json.JsonElement

private val STATUS_CODES = mapOf(
    CoachChangeStatus.Applied to "applied",
    CoachChangeStatus.Undone to "undone",
    CoachChangeStatus.Pending to "pending",
    CoachChangeStatus.Decided to "decided",
)

/** Coach changes through `apply_coach_progression`, `undo_coach_change`, `decide_progression_flag` and the table. */
class SupabaseCoachChangesRepository(private val auth: Auth, private val postgrest: Postgrest) :
    CoachChangesRepository {
    override suspend fun apply(sessionId: String): Result<List<CoachChange>> = auth.ownRow {
        coachChangesOf(
            postgrest.rpc("apply_coach_progression", ApplyCoachProgressionParams(sessionId)).decodeAs<JsonElement>(),
        )
    }

    override suspend fun list(planId: String?, statuses: Set<CoachChangeStatus>): Result<List<CoachChange>> =
        auth.ownRow {
            val codes = statuses.mapNotNull { STATUS_CODES[it] }
            coachChangesOf(
                postgrest.from("coach_changes").select {
                    filter {
                        if (planId != null) eq("plan_id", planId)
                        if (codes.isNotEmpty()) isIn("status", codes)
                    }
                    order("created_at", Order.DESCENDING)
                }.decodeAs<JsonElement>(),
            )
        }

    override suspend fun undo(id: String): Result<UndoResult> = auth.ownRow {
        postgrest.rpc("undo_coach_change", UndoCoachChangeParams(id)).decodeAs<UndoResultDto>().toDomain()
    }.mapChangeErrors()

    override suspend fun decide(id: String, decision: ProgressionFlagDecision): Result<FlagDecisionResult> =
        auth.ownRow {
            val body = postgrest.rpc("decide_progression_flag", DecideProgressionFlagParams(id, decision.code))
                .decodeAs<JsonElement>()
            flagDecisionOf(body) ?: throw AppException(AppError.Unknown)
        }.mapChangeErrors()

    /** P0001 with the server's code in the message (`coach_change_*`); P0002 is a foreign or unknown id. */
    private fun <T> Result<T>.mapChangeErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        val text = "${cause.message.orEmpty()} ${cause.description.orEmpty()}"
        when {
            text.contains("coach_change_conflict") -> throw CoachChangeException(CoachChangeFailure.Conflict, cause)

            text.contains("coach_change_not_undoable") ->
                throw CoachChangeException(CoachChangeFailure.NotUndoable, cause)

            text.contains("coach_change_not_pending") ->
                throw CoachChangeException(CoachChangeFailure.NotPending, cause)

            cause.code == NOT_FOUND_CODE -> throw AppException(AppError.NotFound, cause)

            else -> throw failure
        }
    }

    private companion object {
        const val NOT_FOUND_CODE = "P0002"
    }
}
