package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.DiscomfortExerciseParams
import io.github.lopezjavier.appfitness.data.remote.DiscomfortFollowUpDto
import io.github.lopezjavier.appfitness.data.remote.SwapCandidateDto
import io.github.lopezjavier.appfitness.data.remote.SwapFlaggedParams
import io.github.lopezjavier.appfitness.domain.model.DiscomfortFollowUp
import io.github.lopezjavier.appfitness.domain.model.SwapCandidate
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/**
 * Follow-up of a severe discomfort through `coach_discomfort_followups`, `coach_swap_candidates`,
 * `coach_swap_flagged_exercise`, `decline_discomfort_swap` and `dismiss_discomfort_note`. No Edge Function call.
 * The 14-day and 3-log triggers live in SQL (server `now()`); nothing is recomputed here.
 */
class SupabaseDiscomfortFollowUpRepository(private val auth: Auth, private val postgrest: Postgrest) :
    DiscomfortFollowUpRepository {
    override suspend fun followUps(): Result<List<DiscomfortFollowUp>> = auth.ownRow {
        postgrest.rpc("coach_discomfort_followups").decodeList<DiscomfortFollowUpDto>().map { it.toDomain() }
    }

    override suspend fun swapCandidates(exerciseId: String): Result<List<SwapCandidate>> = auth.ownRow {
        postgrest.rpc("coach_swap_candidates", DiscomfortExerciseParams(exerciseId))
            .decodeList<SwapCandidateDto>()
            .map { it.toDomain() }
    }

    override suspend fun swap(exerciseId: String, newExerciseId: String): Result<Int> = auth.ownRow {
        val body = postgrest.rpc("coach_swap_flagged_exercise", SwapFlaggedParams(exerciseId, newExerciseId))
            .decodeAs<JsonElement>()
        (body as JsonObject)["routines_changed"]?.jsonPrimitive?.int ?: 0
    }

    override suspend fun declineSwap(exerciseId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("decline_discomfort_swap", DiscomfortExerciseParams(exerciseId))
        Unit
    }

    override suspend fun dismissNote(exerciseId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("dismiss_discomfort_note", DiscomfortExerciseParams(exerciseId))
        Unit
    }
}
