package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.MealTemplateDto
import io.github.lopezjavier.appfitness.data.remote.RenameTemplateDto
import io.github.lopezjavier.appfitness.data.remote.SaveMealTemplateParams
import io.github.lopezjavier.appfitness.data.remote.SaveTemplateItemParam
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealTemplate
import io.github.lopezjavier.appfitness.domain.model.NewTemplateItem
import io.github.lopezjavier.appfitness.domain.repository.MealTemplateRepository

// Raised by the table constraints and `save_meal_template`.
private const val UNIQUE_VIOLATION = "23505"
private const val INVALID_PARAMETER = "22023"
private const val CHECK_VIOLATION = "23514"
private const val NO_DATA_FOUND = "P0002"

/** The signed-in user's `meal_templates` (RLS owner policies); a save is one atomic RPC. */
class SupabaseMealTemplateRepository(private val auth: Auth, private val postgrest: Postgrest) :
    MealTemplateRepository {
    override suspend fun list(): Result<List<MealTemplate>> = auth.ownRow {
        postgrest.from("meal_templates")
            .select(Columns.raw(MealTemplateDto.COLUMNS)) { order("name", Order.ASCENDING) }
            .decodeList<MealTemplateDto>()
            .map { it.toDomain() }
    }

    override suspend fun save(
        id: String?,
        name: String,
        mealSlotId: String?,
        items: List<NewTemplateItem>,
    ): Result<String> = auth.ownRow {
        postgrest.rpc(
            "save_meal_template",
            SaveMealTemplateParams(id, name.trim(), mealSlotId, items.map(SaveTemplateItemParam::from)),
        ).decodeAs<String>()
    }.mapTemplateErrors()

    override suspend fun rename(id: String, name: String): Result<Unit> = auth.ownRow {
        postgrest.from("meal_templates").update(RenameTemplateDto(name.trim())) { filter { eq("id", id) } }
        Unit
    }.mapTemplateErrors()

    override suspend fun delete(id: String): Result<Unit> = auth.ownRow {
        postgrest.from("meal_templates").delete { filter { eq("id", id) } }
        Unit
    }

    private fun <T> Result<T>.mapTemplateErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            UNIQUE_VIOLATION -> throw AppException(AppError.NameAlreadyExists, cause)
            INVALID_PARAMETER, CHECK_VIOLATION -> throw AppException(AppError.Validation, cause)
            NO_DATA_FOUND -> throw AppException(AppError.NotFound, cause)
            else -> throw failure
        }
    }
}
