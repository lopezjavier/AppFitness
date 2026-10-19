package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ExerciseHistoryDto
import io.github.lopezjavier.appfitness.data.remote.ExerciseHistoryParams
import io.github.lopezjavier.appfitness.data.remote.LastPerformanceParams
import io.github.lopezjavier.appfitness.data.remote.PreviousSetDto
import io.github.lopezjavier.appfitness.data.remote.SessionIdParams
import io.github.lopezjavier.appfitness.data.remote.StartSessionParams
import io.github.lopezjavier.appfitness.data.remote.WorkoutSessionDto
import io.github.lopezjavier.appfitness.data.remote.WorkoutSetUpsertDto
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Workout sessions of the signed-in user (RLS scopes every query); lifecycle changes use atomic RPCs. */
class SupabaseWorkoutSessionRepository(private val auth: Auth, private val postgrest: Postgrest) :
    WorkoutSessionRepository {
    override suspend fun startFromRoutine(routineId: String): Result<String> = start(routineId)

    override suspend fun startEmpty(): Result<String> = start(null)

    private suspend fun start(routineId: String?): Result<String> = auth.ownRow {
        postgrest.rpc("start_workout_session", StartSessionParams(routineId)).decodeAs<String>()
    }

    override suspend fun getInProgress(): Result<WorkoutSession?> = auth.ownRow {
        postgrest.from("workout_sessions")
            .select(Columns.raw(WorkoutSessionDto.COLUMNS)) { filter { eq("status", "in_progress") } }
            .decodeList<WorkoutSessionDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun upsertSet(
        sessionId: String,
        exerciseId: String,
        exercisePosition: Int,
        supersetGroup: Int?,
        setIndex: Int,
        entry: SetEntry,
        completedAt: Instant?,
        setType: SetType,
    ): Result<Unit> = auth.ownRow {
        val values = entry as SetEntry.WeightReps
        val dto = WorkoutSetUpsertDto(
            sessionId = sessionId,
            exerciseId = exerciseId,
            exercisePosition = exercisePosition,
            supersetGroup = supersetGroup,
            setIndex = setIndex,
            setType = setType,
            weightKg = values.weightKg,
            reps = values.reps,
            completedAt = completedAt?.toString(),
        )
        postgrest.from("workout_sets").upsert(dto) { onConflict = "session_id,exercise_position,set_index" }
    }.map {}

    override suspend fun finish(sessionId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("finish_workout_session", SessionIdParams(sessionId))
    }.map {}

    override suspend fun discard(sessionId: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("discard_workout_session", SessionIdParams(sessionId))
    }.map {}

    override suspend fun lastPerformance(exerciseId: String): Result<List<PreviousSet>> = auth.ownRow {
        postgrest.rpc("last_performance", LastPerformanceParams(exerciseId))
            .decodeList<PreviousSetDto>()
            .map { it.toDomain() }
    }

    override suspend fun exerciseHistory(exerciseId: String, from: LocalDate?): Result<List<ExerciseHistoryPoint>> =
        auth.ownRow {
            postgrest.rpc("exercise_history", ExerciseHistoryParams(exerciseId, from))
                .decodeList<ExerciseHistoryDto>()
                .map { it.toDomain() }
        }
}
