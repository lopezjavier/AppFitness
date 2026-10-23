package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ExerciseOptionsDto
import io.github.lopezjavier.appfitness.data.remote.SetDoubleDumbbellParams
import io.github.lopezjavier.appfitness.domain.model.ExerciseOptions
import io.github.lopezjavier.appfitness.domain.repository.ExerciseOptionsRepository

/** The signed-in user's `exercise_options` rows (RLS owner policies, primary key `(user_id, exercise_id)`). */
class SupabaseExerciseOptionsRepository(private val auth: Auth, private val postgrest: Postgrest) :
    ExerciseOptionsRepository {
    override suspend fun get(): Result<ExerciseOptions> = auth.ownRow {
        val ids = postgrest.from("exercise_options")
            .select(Columns.raw(ExerciseOptionsDto.COLUMNS)) { filter { eq("double_dumbbell", true) } }
            .decodeList<ExerciseOptionsDto>()
            .map { it.exerciseId }
        ExerciseOptions(ids.toSet())
    }

    override suspend fun setDoubleDumbbell(exerciseId: String, enabled: Boolean): Result<Unit> = auth.ownRow {
        postgrest.rpc("set_exercise_double_dumbbell", SetDoubleDumbbellParams(exerciseId, enabled))
        Unit
    }
}
