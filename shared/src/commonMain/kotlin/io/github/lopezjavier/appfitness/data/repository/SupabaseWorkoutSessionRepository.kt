package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.DeleteSetParams
import io.github.lopezjavier.appfitness.data.remote.ExerciseHistoryDto
import io.github.lopezjavier.appfitness.data.remote.ExerciseHistoryParams
import io.github.lopezjavier.appfitness.data.remote.ExerciseSessionRecordsDto
import io.github.lopezjavier.appfitness.data.remote.LastPerformanceParams
import io.github.lopezjavier.appfitness.data.remote.PauseParams
import io.github.lopezjavier.appfitness.data.remote.PersonalRecordDto
import io.github.lopezjavier.appfitness.data.remote.PreviousSetDto
import io.github.lopezjavier.appfitness.data.remote.SessionIdParams
import io.github.lopezjavier.appfitness.data.remote.StartSessionParams
import io.github.lopezjavier.appfitness.data.remote.WorkoutSessionDto
import io.github.lopezjavier.appfitness.data.remote.WorkoutSetUpsertDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.columns
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private const val UNIQUE_VIOLATION = "23505"

/** Workout sessions of the signed-in user (RLS scopes every query); lifecycle changes use atomic RPCs. */
@Suppress("TooManyFunctions") // One small function per repository operation.
class SupabaseWorkoutSessionRepository(private val auth: Auth, private val postgrest: Postgrest) :
    WorkoutSessionRepository {
    override suspend fun startFromRoutine(routineId: String): Result<String> = auth.ownRow {
        postgrest.rpc("start_workout_session", StartSessionParams(routineId)).decodeAs<String>()
    }.recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause
        if (cause is PostgrestRestException && cause.code == UNIQUE_VIOLATION) {
            throw AppException(AppError.SessionAlreadyInProgress, cause)
        }
        throw failure
    }

    override suspend fun getInProgress(): Result<WorkoutSession?> = auth.ownRow {
        postgrest.from("workout_sessions")
            .select(Columns.raw(WorkoutSessionDto.COLUMNS)) { filter { eq("status", "in_progress") } }
            .decodeList<WorkoutSessionDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun listCompleted(offset: Int, limit: Int): Result<List<WorkoutSession>> = auth.ownRow {
        postgrest.from("workout_sessions")
            .select(Columns.raw(WorkoutSessionDto.COLUMNS)) {
                filter { eq("status", "completed") }
                order("started_at", Order.DESCENDING)
                order("id", Order.DESCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }
            .decodeList<WorkoutSessionDto>()
            .map { it.toDomain() }
    }

    override suspend fun exerciseSessions(exerciseId: String, offset: Int, limit: Int): Result<List<WorkoutSession>> =
        auth.ownRow {
            postgrest.from("workout_sessions")
                .select(Columns.raw(WorkoutSessionDto.INNER_COLUMNS)) {
                    filter {
                        eq("status", "completed")
                        eq("workout_sets.exercise_id", exerciseId)
                        filterNot("workout_sets.completed_at", FilterOperator.IS, "null")
                    }
                    order("started_at", Order.DESCENDING)
                    order("id", Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
                .decodeList<WorkoutSessionDto>()
                .map { it.toDomain() }
        }

    override suspend fun getSession(sessionId: String): Result<WorkoutSession?> = auth.ownRow {
        postgrest.from("workout_sessions")
            .select(Columns.raw(WorkoutSessionDto.COLUMNS)) {
                filter {
                    eq("id", sessionId)
                    eq("status", "completed")
                }
            }
            .decodeList<WorkoutSessionDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> = auth.ownRow {
        postgrest.from("workout_sessions").delete { filter { eq("id", sessionId) } }
    }.map {}

    override suspend fun upsertSet(
        sessionId: String,
        exerciseId: String,
        exercisePosition: Int,
        supersetGroup: Int?,
        setIndex: Int,
        entry: SetEntry,
        completedAt: Instant?,
        setType: SetType,
        rpe: Double?,
    ): Result<Unit> = auth.ownRow {
        val columns = entry.columns()
        val dto = WorkoutSetUpsertDto(
            sessionId = sessionId,
            exerciseId = exerciseId,
            exercisePosition = exercisePosition,
            supersetGroup = supersetGroup,
            setIndex = setIndex,
            setType = setType,
            // weight_kg and reps are NOT NULL in the database: metrics without them write 0.
            weightKg = columns.weightKg ?: 0.0,
            reps = columns.reps ?: 0,
            durationS = columns.durationS,
            distanceM = columns.distanceM,
            steps = columns.steps,
            floors = columns.floors,
            rpe = rpe,
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

    override suspend fun pause(sessionId: String, at: Instant): Result<Unit> = auth.ownRow {
        postgrest.rpc("pause_workout_session", PauseParams(sessionId, at.toString()))
    }.map {}

    override suspend fun resume(sessionId: String, at: Instant): Result<Unit> = auth.ownRow {
        postgrest.rpc("resume_workout_session", PauseParams(sessionId, at.toString()))
    }.map {}

    override suspend fun deleteSet(sessionId: String, exercisePosition: Int, setIndex: Int): Result<Unit> =
        auth.ownRow {
            postgrest.rpc("delete_workout_set", DeleteSetParams(sessionId, exercisePosition, setIndex))
        }.map {}

    override suspend fun lastPerformance(exerciseId: String, routineId: String?): Result<List<PreviousSet>> =
        auth.ownRow {
            postgrest.rpc("last_performance", LastPerformanceParams(exerciseId, routineId))
                .decodeList<PreviousSetDto>()
                .map { it.toDomain() }
        }

    override suspend fun exerciseHistory(exerciseId: String, from: LocalDate?): Result<List<ExerciseHistoryPoint>> =
        auth.ownRow {
            postgrest.rpc("exercise_history", ExerciseHistoryParams(exerciseId, from))
                .decodeList<ExerciseHistoryDto>()
                .map { it.toDomain() }
        }

    override suspend fun personalRecords(exerciseId: String): Result<List<PersonalRecord>> = auth.ownRow {
        postgrest.rpc("personal_records", LastPerformanceParams(exerciseId))
            .decodeList<PersonalRecordDto>()
            .mapNotNull { it.toDomain() }
    }

    override suspend fun sessionRecords(exerciseId: String): Result<List<ExerciseSessionRecords>> = auth.ownRow {
        postgrest.rpc("exercise_session_records", LastPerformanceParams(exerciseId))
            .decodeList<ExerciseSessionRecordsDto>()
            .map { it.toDomain() }
    }
}
