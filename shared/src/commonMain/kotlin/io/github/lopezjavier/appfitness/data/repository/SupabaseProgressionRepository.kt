package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ProgressionParams
import io.github.lopezjavier.appfitness.data.remote.ProgressionSuggestionDto
import io.github.lopezjavier.appfitness.domain.model.ProgressionSuggestion
import io.github.lopezjavier.appfitness.domain.repository.ProgressionRepository

/** Progression suggestions through the `progression_suggestions` RPC (reads only the caller's own data). */
class SupabaseProgressionRepository(private val auth: Auth, private val postgrest: Postgrest) : ProgressionRepository {
    override suspend fun suggestionsFor(sessionId: String): Result<List<ProgressionSuggestion>> = auth.ownRow {
        postgrest.rpc("progression_suggestions", ProgressionParams(sessionId))
            .decodeList<ProgressionSuggestionDto>()
            .mapNotNull { it.toDomain() }
    }
}
