package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.lopezjavier.appfitness.data.remote.ProfileDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/** Reads the signed-in user's `profiles` row (RLS `profiles_select_own`). */
class SupabaseProfileRepository(private val auth: Auth, private val postgrest: Postgrest) : ProfileRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun getOwnProfile(): Result<ProfileSummary> {
        val userId = auth.currentUserOrNull()?.id ?: return Result.failure(AppException(AppError.Unauthorized))
        return try {
            val dto =
                postgrest.from("profiles")
                    .select(Columns.raw(ProfileDto.COLUMNS)) { filter { eq("id", userId) } }
                    .decodeSingle<ProfileDto>()
            Result.success(dto.toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Result.failure(AppException(failure.toAppError(), failure))
        }
    }
}

internal fun Throwable.toAppError(): AppError = when (this) {
    is UnauthorizedRestException -> AppError.Unauthorized
    is NotFoundRestException -> AppError.NotFound
    is RestException -> AppError.Unknown
    is HttpRequestException, is HttpRequestTimeoutException, is IOException -> AppError.Network
    else -> AppError.Unknown
}
