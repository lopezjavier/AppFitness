package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.DeloadSuggestionsParams
import io.github.lopezjavier.appfitness.data.remote.deloadSuggestionsOf
import io.github.lopezjavier.appfitness.domain.model.DeloadExerciseSuggestion
import io.github.lopezjavier.appfitness.domain.repository.DeloadRepository
import kotlinx.serialization.json.JsonElement

/** Deload sets of a live session through the `deload_suggestions` RPC (reads only the caller's own data). */
class SupabaseDeloadRepository(private val auth: Auth, private val postgrest: Postgrest) : DeloadRepository {
    override suspend fun suggestionsFor(sessionId: String): Result<List<DeloadExerciseSuggestion>> = auth.ownRow {
        val body = postgrest.rpc("deload_suggestions", DeloadSuggestionsParams(sessionId)).decodeAs<JsonElement>()
        deloadSuggestionsOf(body)
    }
}
