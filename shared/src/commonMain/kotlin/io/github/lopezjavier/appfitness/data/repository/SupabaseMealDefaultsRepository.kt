package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.ApplyMealDefaultsParams
import io.github.lopezjavier.appfitness.data.remote.MealDefaultDto
import io.github.lopezjavier.appfitness.data.remote.SaveMealDefaultParam
import io.github.lopezjavier.appfitness.data.remote.SaveMealDefaultsParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealDefault
import io.github.lopezjavier.appfitness.domain.model.MealDefaultAssignment
import io.github.lopezjavier.appfitness.domain.repository.MealDefaultsRepository
import kotlinx.datetime.LocalDate

// Raised by `save_meal_defaults` and `apply_meal_defaults`.
private const val DEFAULTS_INVALID_PARAMETER = "22023"
private const val DEFAULTS_CHECK_VIOLATION = "23514"
private const val DEFAULTS_NO_DATA_FOUND = "P0002"

/** The signed-in user's `meal_defaults` (RLS owner policies); saving and applying are atomic RPCs. */
class SupabaseMealDefaultsRepository(private val auth: Auth, private val postgrest: Postgrest) :
    MealDefaultsRepository {
    override suspend fun list(): Result<List<MealDefault>> = auth.ownRow {
        postgrest.from("meal_defaults")
            .select(Columns.raw(MealDefaultDto.COLUMNS))
            .decodeList<MealDefaultDto>()
            .map { it.toDomain() }
    }

    override suspend fun save(assignments: List<MealDefaultAssignment>): Result<Unit> = auth.ownRow {
        postgrest.rpc(
            "save_meal_defaults",
            SaveMealDefaultsParams(assignments.map(SaveMealDefaultParam::from)),
        )
        Unit
    }.mapDefaultsErrors()

    override suspend fun applyForDate(date: LocalDate): Result<Int> = auth.ownRow {
        postgrest.rpc("apply_meal_defaults", ApplyMealDefaultsParams(date.toString())).decodeAs<Int>()
    }.mapDefaultsErrors()

    private fun <T> Result<T>.mapDefaultsErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            DEFAULTS_INVALID_PARAMETER, DEFAULTS_CHECK_VIOLATION -> throw AppException(AppError.Validation, cause)
            DEFAULTS_NO_DATA_FOUND -> throw AppException(AppError.NotFound, cause)
            else -> throw failure
        }
    }
}
