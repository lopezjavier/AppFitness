package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.DeleteMealSlotParams
import io.github.lopezjavier.appfitness.data.remote.MealSlotDraftDto
import io.github.lopezjavier.appfitness.data.remote.MealSlotDto
import io.github.lopezjavier.appfitness.data.remote.SaveMealSlotsParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealSlotDraft
import io.github.lopezjavier.appfitness.domain.repository.MealSlotRepository

// Raised by `save_meal_slots` / `delete_meal_slot`: last meal or meal with entries, bad shape, foreign slot.
private const val CHECK_VIOLATION = "23514"
private const val INVALID_PARAMETER = "22023"
private const val NO_DATA_FOUND = "P0002"

/** The signed-in user's `meal_slots` (RLS owner policies); the structure is saved by one atomic RPC. */
class SupabaseMealSlotRepository(private val auth: Auth, private val postgrest: Postgrest) : MealSlotRepository {
    override suspend fun listEnabled(): Result<List<MealSlot>> = auth.ownRow {
        postgrest.from("meal_slots")
            .select(Columns.raw(MealSlotDto.COLUMNS)) {
                filter { eq("enabled", true) }
                order("position", Order.ASCENDING)
            }
            .decodeList<MealSlotDto>()
            .map { it.toDomain() }
    }

    override suspend fun listAll(): Result<List<MealSlot>> = auth.ownRow {
        postgrest.from("meal_slots")
            .select(Columns.raw(MealSlotDto.COLUMNS)) { order("position", Order.ASCENDING) }
            .decodeList<MealSlotDto>()
            .map { it.toDomain() }
    }

    override suspend fun save(slots: List<MealSlotDraft>): Result<Unit> = auth.ownRow {
        postgrest.rpc("save_meal_slots", SaveMealSlotsParams(slots.map(MealSlotDraftDto::from)))
        Unit
    }.mapRpcErrors()

    override suspend fun delete(id: String): Result<Unit> = auth.ownRow {
        postgrest.rpc("delete_meal_slot", DeleteMealSlotParams(id))
        Unit
    }.mapRpcErrors()

    private fun Result<Unit>.mapRpcErrors(): Result<Unit> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            CHECK_VIOLATION, INVALID_PARAMETER -> throw AppException(AppError.Validation, cause)
            NO_DATA_FOUND -> throw AppException(AppError.NotFound, cause)
            else -> throw failure
        }
    }
}
