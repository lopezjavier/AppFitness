package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.BodyMeasurementDto
import io.github.lopezjavier.appfitness.data.remote.BodyMeasurementInsertDto
import io.github.lopezjavier.appfitness.data.remote.BodyMeasurementUpdateDto
import io.github.lopezjavier.appfitness.data.remote.DeleteBodyMeasurementParams
import io.github.lopezjavier.appfitness.data.remote.HcRecordIdUpdateDto
import io.github.lopezjavier.appfitness.data.remote.MeasurementSourceDto
import io.github.lopezjavier.appfitness.data.remote.SaveBodyMeasurementParams
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlin.time.Instant

// Raised by `save_body_measurement` and the table checks.
private const val INVALID_PARAMETER = "22023"
private const val CHECK_VIOLATION = "23514"
private const val NO_DATA_FOUND = "P0002"

private val COMPOSITION_COLUMNS = listOf("body_fat_pct", "lean_mass_kg", "water_kg", "bone_kg", "bmr_kcal")
private const val COMPOSITION_LOOKBACK = 50L

/**
 * `body_measurements` of the signed-in user (RLS owner policies). The onboarding weight is the user's earliest
 * `manual` row by `created_at` (id as the tie-break): no other writer exists before onboarding completes.
 */
@Suppress("TooManyFunctions") // One method per port operation.
class SupabaseBodyMeasurementRepository(private val auth: Auth, private val postgrest: Postgrest) :
    BodyMeasurementRepository {
    override suspend fun getOnboardingWeight(): Result<BodyMeasurement?> = signedIn { findOnboardingRow() }

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit> = signedIn {
        val existing = findOnboardingRow()
        val table = postgrest.from("body_measurements")
        if (existing != null) {
            table.update(BodyMeasurementUpdateDto(weightKg, measuredAt.toString())) {
                filter { eq("id", existing.id) }
            }
        } else {
            table.insert(BodyMeasurementInsertDto(weightKg, measuredAt.toString(), MeasurementSourceDto.Manual))
        }
    }.map {}

    override suspend fun getLatestWeight(): Result<BodyMeasurement?> = signedIn {
        postgrest.from("body_measurements")
            .select(Columns.raw(BodyMeasurementDto.COLUMNS)) {
                order("measured_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<BodyMeasurementDto>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun getMeasurements(from: Instant, toExclusive: Instant): Result<List<BodyMeasurement>> =
        signedIn {
            postgrest.from("body_measurements")
                .select(Columns.raw(BodyMeasurementDto.COLUMNS)) {
                    // Two operators on one column need `and(...)`: plain filters are keyed by column.
                    filter {
                        and {
                            gte("measured_at", from.toString())
                            lt("measured_at", toExclusive.toString())
                        }
                    }
                    order("measured_at", Order.DESCENDING)
                }
                .decodeList<BodyMeasurementDto>()
                .map { it.toDomain() }
        }

    override suspend fun getLatestComposition(): Result<LatestComposition> = signedIn {
        postgrest.from("body_measurements")
            .select(Columns.raw(BodyMeasurementDto.COLUMNS)) {
                filter {
                    or {
                        COMPOSITION_COLUMNS.forEach { filterNot(it, FilterOperator.IS, "null") }
                    }
                }
                order("measured_at", Order.DESCENDING)
                limit(COMPOSITION_LOOKBACK)
            }
            .decodeList<BodyMeasurementDto>()
            .map { it.toDomain() }
            .let { LatestComposition.from(it) }
    }

    override suspend fun saveManualWeight(save: ManualWeightSave): Result<String> = signedIn {
        postgrest.rpc("save_body_measurement", SaveBodyMeasurementParams.from(save)).decodeAs<String>()
    }.mapSaveErrors()

    override suspend fun deleteMeasurement(id: String): Result<Unit> = signedIn {
        postgrest.rpc("delete_body_measurement", DeleteBodyMeasurementParams(id))
        Unit
    }

    override suspend fun setHcRecordId(id: String, hcRecordId: String?): Result<Unit> = signedIn {
        postgrest.from("body_measurements").update(HcRecordIdUpdateDto(hcRecordId)) {
            filter { eq("id", id) }
        }
        Unit
    }

    private fun <T> Result<T>.mapSaveErrors(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        when (cause.code) {
            INVALID_PARAMETER, CHECK_VIOLATION -> throw AppException(AppError.Validation, cause)
            NO_DATA_FOUND -> throw AppException(AppError.NotFound, cause)
            else -> throw failure
        }
    }

    private suspend fun findOnboardingRow(): BodyMeasurement? = postgrest.from("body_measurements")
        .select(Columns.raw(BodyMeasurementDto.COLUMNS)) {
            filter { eq("source", "manual") }
            order("created_at", Order.ASCENDING)
            order("id", Order.ASCENDING)
            limit(1)
        }
        .decodeList<BodyMeasurementDto>()
        .firstOrNull()
        ?.toDomain()

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}
