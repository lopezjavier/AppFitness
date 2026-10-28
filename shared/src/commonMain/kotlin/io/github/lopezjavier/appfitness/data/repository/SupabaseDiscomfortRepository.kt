package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.DiscomfortDto
import io.github.lopezjavier.appfitness.data.remote.DiscomfortInsertDto
import io.github.lopezjavier.appfitness.data.remote.DiscomfortResolveDto
import io.github.lopezjavier.appfitness.domain.model.Discomfort
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import kotlin.time.Instant

/** `exercise_discomforts` of the signed-in user (RLS owner policies; update limited to `resolved_at`). */
class SupabaseDiscomfortRepository(private val auth: Auth, private val postgrest: Postgrest) : DiscomfortRepository {
    override suspend fun log(exerciseId: String, note: String?, severe: Boolean): Result<Discomfort> = auth.ownRow {
        postgrest.from("exercise_discomforts")
            .insert(DiscomfortInsertDto(exerciseId, note, severe)) {
                select(Columns.raw(DiscomfortDto.COLUMNS))
            }
            .decodeSingle<DiscomfortDto>()
            .toDomain()
    }

    override suspend fun resolveOpen(exerciseId: String, at: Instant): Result<Unit> = auth.ownRow {
        postgrest.from("exercise_discomforts").update(DiscomfortResolveDto(at)) {
            filter {
                eq("exercise_id", exerciseId)
                exact("resolved_at", null)
            }
        }
        Unit
    }

    override suspend fun unresolved(exerciseIds: Set<String>): Result<List<Discomfort>> = auth.ownRow {
        if (exerciseIds.isEmpty()) return@ownRow emptyList()
        postgrest.from("exercise_discomforts")
            .select(Columns.raw(DiscomfortDto.COLUMNS)) {
                filter {
                    isIn("exercise_id", exerciseIds.toList())
                    exact("resolved_at", null)
                }
                order("logged_at", Order.DESCENDING)
            }
            .decodeList<DiscomfortDto>()
            .map { it.toDomain() }
    }
}
