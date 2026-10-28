package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ExerciseSetCountRowDto
import io.github.lopezjavier.appfitness.data.remote.ExerciseSetCountsParams
import io.github.lopezjavier.appfitness.data.remote.MuscleGroupSetsRowDto
import io.github.lopezjavier.appfitness.data.remote.StatsWindowParams
import io.github.lopezjavier.appfitness.data.remote.TrainingSummaryRowDto
import io.github.lopezjavier.appfitness.data.remote.WeekRecordRowDto
import io.github.lopezjavier.appfitness.data.remote.WeeklySetCountRowDto
import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.repository.TrainingStatsRepository
import kotlinx.datetime.LocalDate

/** Training statistics of the signed-in user through the read-only statistics RPCs (RLS scopes them). */
class SupabaseTrainingStatsRepository(private val auth: Auth, private val postgrest: Postgrest) :
    TrainingStatsRepository {
    override suspend fun summary(from: LocalDate?, to: LocalDate?): Result<TrainingSummary> = auth.ownRow {
        postgrest.rpc("training_summary", StatsWindowParams(from, to))
            .decodeList<TrainingSummaryRowDto>()
            .firstOrNull()?.toDomain() ?: TrainingSummary(0, 0, 0.0, 0)
    }

    override suspend fun weeklySetCounts(from: LocalDate?, to: LocalDate?): Result<List<SetCountPoint>> = auth.ownRow {
        postgrest.rpc("weekly_set_counts", StatsWindowParams(from, to))
            .decodeList<WeeklySetCountRowDto>()
            .map { it.toDomain() }
    }

    override suspend fun muscleGroupSets(from: LocalDate?, to: LocalDate?): Result<List<MuscleGroupSets>> =
        auth.ownRow {
            postgrest.rpc("muscle_group_sets", StatsWindowParams(from, to))
                .decodeList<MuscleGroupSetsRowDto>()
                .map { it.toDomain() }
        }

    override suspend fun exerciseSetCounts(
        from: LocalDate?,
        to: LocalDate?,
        muscleGroup: MuscleGroup?,
    ): Result<List<ExerciseSetCount>> = auth.ownRow {
        postgrest.rpc("exercise_set_counts", ExerciseSetCountsParams(from, to, muscleGroup))
            .decodeList<ExerciseSetCountRowDto>()
            .map { it.toDomain() }
    }

    override suspend fun weeklyRecords(from: LocalDate, to: LocalDate): Result<List<WeekRecord>> = auth.ownRow {
        postgrest.rpc("weekly_records", StatsWindowParams(from, to))
            .decodeList<WeekRecordRowDto>()
            .mapNotNull { it.toDomain() }
    }
}
