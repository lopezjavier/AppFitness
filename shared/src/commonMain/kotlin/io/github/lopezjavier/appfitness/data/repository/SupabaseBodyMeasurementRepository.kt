package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.BodyMeasurementDto
import io.github.lopezjavier.appfitness.data.remote.BodyMeasurementInsertDto
import io.github.lopezjavier.appfitness.data.remote.BodyMeasurementUpdateDto
import io.github.lopezjavier.appfitness.data.remote.MeasurementSourceDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import kotlin.time.Instant

/**
 * `body_measurements` of the signed-in user (RLS owner policies). The onboarding weight is the user's earliest
 * `manual` row by `created_at` (id as the tie-break): no other writer exists before onboarding completes.
 */
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
