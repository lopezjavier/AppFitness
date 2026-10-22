package io.github.lopezjavier.appfitness.data.repository

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.github.lopezjavier.appfitness.data.remote.BiometricsDto
import io.github.lopezjavier.appfitness.data.remote.BiometricsUpdateDto
import io.github.lopezjavier.appfitness.data.remote.DisplayNameUpdateDto
import io.github.lopezjavier.appfitness.data.remote.OnboardingCompletedDto
import io.github.lopezjavier.appfitness.data.remote.ProfileDto
import io.github.lopezjavier.appfitness.data.remote.SavePreferencesParams
import io.github.lopezjavier.appfitness.data.remote.TargetWeightDto
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonPrimitive

/** Reads and updates the signed-in user's `profiles` row (RLS `profiles_select_own` / `profiles_update_own`). */
class SupabaseProfileRepository(private val auth: Auth, private val postgrest: Postgrest) : ProfileRepository {
    override suspend fun getOwnProfile(): Result<ProfileSummary> = ownRow { userId ->
        postgrest.from("profiles")
            .select(Columns.raw(ProfileDto.COLUMNS)) { filter { eq("id", userId) } }
            .decodeSingle<ProfileDto>()
            .toDomain()
    }

    override suspend fun getBiometrics(): Result<SavedBiometrics> = ownRow { userId ->
        postgrest.from("profiles")
            .select(Columns.raw(BiometricsDto.COLUMNS)) { filter { eq("id", userId) } }
            .decodeSingle<BiometricsDto>()
            .toDomain()
    }

    override suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit> = ownRow { userId ->
        postgrest.from("profiles").update(BiometricsUpdateDto.from(biometrics)) { filter { eq("id", userId) } }
    }.map {}

    override suspend fun markOnboardingCompleted(): Result<Unit> = ownRow { userId ->
        postgrest.from("profiles").update(OnboardingCompletedDto(true)) { filter { eq("id", userId) } }
    }.map {}

    override suspend fun updateDisplayName(name: String): Result<Unit> = ownRow { userId ->
        postgrest.from("profiles").update(DisplayNameUpdateDto(name)) { filter { eq("id", userId) } }
    }.map {}

    override suspend fun getTargetWeight(): Result<Double?> = ownRow { userId ->
        postgrest.from("profiles")
            .select(Columns.raw(TargetWeightDto.COLUMNS)) { filter { eq("id", userId) } }
            .decodeSingle<TargetWeightDto>()
            .targetWeightKg
    }

    override suspend fun setTargetWeight(kg: Double?): Result<Unit> = ownRow { userId ->
        postgrest.from("profiles").update(TargetWeightDto(kg)) { filter { eq("id", userId) } }
    }.map {}

    override fun hasProvidedFullName(): Boolean {
        val fullName = auth.currentUserOrNull()?.userMetadata?.get("full_name") as? JsonPrimitive
        return fullName?.takeIf { it.isString }?.content?.isNotBlank() == true
    }

    override suspend fun savePreferences(
        displayName: String,
        biometrics: Biometrics,
        goals: NutritionGoals,
        weight: WeightEntry?,
        warmUpIncluded: Boolean,
    ): Result<Unit> = runMappingErrors {
        postgrest.rpc(
            "save_preferences",
            SavePreferencesParams.from(displayName, biometrics, goals, weight, warmUpIncluded),
        )
    }.map {}

    private suspend fun <T> ownRow(block: suspend (userId: String) -> T): Result<T> = auth.ownRow(block)
}

/** Runs [block] with the signed-in user's id, or fails with [AppError.Unauthorized] when nobody is signed in. */
internal suspend fun <T> Auth.ownRow(block: suspend (userId: String) -> T): Result<T> {
    val userId = currentUserOrNull()?.id ?: return Result.failure(AppException(AppError.Unauthorized))
    return runMappingErrors { block(userId) }
}

/** Runs [block], mapping any failure (except cancellation) to an [AppException] carrying an [AppError]. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runMappingErrors(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    Result.failure(AppException(failure.toAppError(), failure))
}

internal fun Throwable.toAppError(): AppError = when (this) {
    is AppException -> error
    is UnauthorizedRestException -> AppError.Unauthorized
    is NotFoundRestException -> AppError.NotFound
    is RestException -> AppError.Unknown
    is HttpRequestException, is HttpRequestTimeoutException, is IOException -> AppError.Network
    else -> AppError.Unknown
}
