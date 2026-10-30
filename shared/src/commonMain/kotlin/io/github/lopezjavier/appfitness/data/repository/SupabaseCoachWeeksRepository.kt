package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.DecideDeloadParams
import io.github.lopezjavier.appfitness.data.remote.EvaluateDeloadParams
import io.github.lopezjavier.appfitness.data.remote.EvaluateDeloadResultDto
import io.github.lopezjavier.appfitness.data.remote.PlanWeekDto
import io.github.lopezjavier.appfitness.data.remote.PlanWeeksParams
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.DeloadProposal
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import kotlinx.datetime.LocalDate

private const val WRONG_OBJECT_STATE = "55000"

/** Weeks and deload through the `coach_plan_weeks`, `evaluate_coach_deload` and `decide_coach_deload` RPCs. */
class SupabaseCoachWeeksRepository(private val auth: Auth, private val postgrest: Postgrest) : CoachWeeksRepository {
    override suspend fun weeks(planId: String, today: LocalDate): Result<List<PlanWeek>> = auth.ownRow {
        postgrest.rpc("coach_plan_weeks", PlanWeeksParams(planId, today))
            .decodeList<PlanWeekDto>()
            .mapNotNull { it.toDomain() }
            .sortedBy { it.week }
    }

    override suspend fun pendingDeload(today: LocalDate): Result<DeloadProposal?> = auth.ownRow {
        postgrest.rpc("evaluate_coach_deload", EvaluateDeloadParams(today))
            .decodeAs<EvaluateDeloadResultDto>()
            .toProposal()
    }

    override suspend fun decideDeload(id: String, accept: Boolean): Result<Unit> = auth.ownRow {
        postgrest.rpc("decide_coach_deload", DecideDeloadParams(id, accept))
        Unit
    }.recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code != WRONG_OBJECT_STATE) throw failure
        throw CoachException(CoachFailure.Conflict)
    }
}
