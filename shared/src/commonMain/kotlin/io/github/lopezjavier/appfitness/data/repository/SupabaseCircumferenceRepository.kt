package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.lopezjavier.appfitness.data.remote.BodyCircumferenceDto
import io.github.lopezjavier.appfitness.data.remote.BodyCircumferenceInsertDto
import io.github.lopezjavier.appfitness.data.remote.BodyCircumferenceUpdateDto
import io.github.lopezjavier.appfitness.data.remote.CircumferenceSiteDto
import io.github.lopezjavier.appfitness.data.remote.MeasurementSourceDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.BodyCircumference
import io.github.lopezjavier.appfitness.domain.model.CircumferenceEntry
import io.github.lopezjavier.appfitness.domain.repository.CircumferenceRepository
import kotlin.time.Instant

private const val CHECK_VIOLATION = "23514"

/** `body_circumferences` of the signed-in user (RLS owner policies); plain table writes. */
class SupabaseCircumferenceRepository(private val auth: Auth, private val postgrest: Postgrest) :
    CircumferenceRepository {
    override suspend fun getAll(): Result<List<BodyCircumference>> = signedIn {
        postgrest.from("body_circumferences")
            .select(Columns.raw(BodyCircumferenceDto.COLUMNS)) {
                order("measured_at", Order.DESCENDING)
            }
            .decodeList<BodyCircumferenceDto>()
            .map { it.toDomain() }
    }

    override suspend fun addAll(entries: List<CircumferenceEntry>, measuredAt: Instant): Result<Unit> = signedIn {
        postgrest.from("body_circumferences").insert(
            entries.map {
                BodyCircumferenceInsertDto(
                    CircumferenceSiteDto.of(it.site),
                    it.valueCm,
                    measuredAt.toString(),
                    MeasurementSourceDto.Manual,
                )
            },
        )
        Unit
    }.mapCheckViolation()

    override suspend fun update(id: String, valueCm: Double, measuredAt: Instant): Result<Unit> = signedIn {
        postgrest.from("body_circumferences").update(BodyCircumferenceUpdateDto(valueCm, measuredAt.toString())) {
            filter { eq("id", id) }
        }
        Unit
    }.mapCheckViolation()

    override suspend fun delete(id: String): Result<Unit> = signedIn {
        postgrest.from("body_circumferences").delete { filter { eq("id", id) } }
        Unit
    }

    private fun <T> Result<T>.mapCheckViolation(): Result<T> = recoverCatching { failure ->
        val cause = (failure as? AppException)?.cause as? PostgrestRestException ?: throw failure
        if (cause.code == CHECK_VIOLATION) throw AppException(AppError.Validation, cause) else throw failure
    }

    private suspend fun <T> signedIn(block: suspend () -> T): Result<T> {
        if (auth.currentUserOrNull() == null) return Result.failure(AppException(AppError.Unauthorized))
        return runMappingErrors(block)
    }
}
