package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.MaxRepsRowDto
import io.github.lopezjavier.appfitness.data.remote.WeeklyMuscleVolumeParams
import io.github.lopezjavier.appfitness.data.remote.WeeklyMuscleVolumeRowDto
import io.github.lopezjavier.appfitness.data.remote.WorkoutSessionDto
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.WeeklyMuscleVolumeRow
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Strength reads of the signed-in user (RLS scopes every query). */
class SupabaseStrengthRepository(private val auth: Auth, private val postgrest: Postgrest) : StrengthRepository {
    override suspend fun recentSessions(since: Instant): Result<List<WorkoutSession>> = auth.ownRow {
        postgrest.from("workout_sessions")
            .select(Columns.raw(WorkoutSessionDto.COLUMNS)) {
                filter {
                    eq("status", "completed")
                    gte("started_at", since.toString())
                }
            }
            .decodeList<WorkoutSessionDto>()
            .map { it.toDomain() }
    }

    override suspend fun maxRepsHistory(exerciseId: String, from: LocalDate?): Result<List<MaxRepsPoint>> =
        auth.ownRow {
            val rows = postgrest.from("workout_sets")
                .select(Columns.raw(MaxRepsRowDto.COLUMNS)) {
                    filter {
                        eq("exercise_id", exerciseId)
                        eq("workout_sessions.status", "completed")
                        neq("set_type", "warm_up")
                        filterNot("completed_at", FilterOperator.IS, "null")
                        if (from != null) gte("workout_sessions.started_at", "${from}T00:00:00Z")
                    }
                }
                .decodeList<MaxRepsRowDto>()
            MaxRepsRowDto.toPoints(rows)
        }

    override suspend fun weeklyMuscleVolume(from: LocalDate): Result<List<WeeklyMuscleVolumeRow>> = auth.ownRow {
        postgrest.rpc("weekly_muscle_volume", WeeklyMuscleVolumeParams(from))
            .decodeList<WeeklyMuscleVolumeRowDto>()
            .map { it.toDomain() }
    }
}
