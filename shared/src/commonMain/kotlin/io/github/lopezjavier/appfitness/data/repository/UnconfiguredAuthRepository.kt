package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * Stand-in used when the build has no Supabase URL / publishable key (a CI-built `local` APK): the app
 * shows Welcome, every sign-in fails, and nothing crashes at launch on a blank key.
 */
class UnconfiguredAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = flowOf(AuthState.SignedOut)

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): SignInOutcome = SignInOutcome.Failed

    override suspend fun signInWithPassword(credentials: TestLoginCredentials): SignInOutcome = SignInOutcome.Failed

    override suspend fun signOut() = Unit
}

/** Profile counterpart of [UnconfiguredAuthRepository]: there is never a profile to read. */
class UnconfiguredProfileRepository : ProfileRepository {
    override suspend fun getOwnProfile(): Result<ProfileSummary> = Result.failure(AppException(AppError.Unauthorized))

    override suspend fun getBiometrics(): Result<SavedBiometrics> = Result.failure(AppException(AppError.Unknown))

    override suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun markOnboardingCompleted(): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun saveSettings(biometrics: Biometrics, goals: NutritionGoals): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}

/** Weight counterpart of [UnconfiguredAuthRepository]: nothing can be read or saved. */
class UnconfiguredBodyMeasurementRepository : BodyMeasurementRepository {
    override suspend fun getOnboardingWeight(): Result<BodyMeasurement?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun getLatestWeight(): Result<BodyMeasurement?> = Result.failure(AppException(AppError.Unknown))
}

/** Goals counterpart of [UnconfiguredAuthRepository]: nothing can be read or saved. */
class UnconfiguredNutritionGoalsRepository : NutritionGoalsRepository {
    override suspend fun getOwn(): Result<NutritionGoals?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun upsert(goals: NutritionGoals): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}

/** Account counterpart of [UnconfiguredAuthRepository]: there is no server to delete from. */
class UnconfiguredAccountRepository : AccountRepository {
    override suspend fun deleteAccount(): DeleteAccountOutcome = DeleteAccountOutcome.Failed
}
