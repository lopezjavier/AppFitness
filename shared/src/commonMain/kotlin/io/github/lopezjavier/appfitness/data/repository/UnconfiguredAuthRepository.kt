package io.github.lopezjavier.appfitness.data.repository

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.Exercise
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.Routine
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
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

    override suspend fun updateDisplayName(name: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun getTargetWeight(): Result<Double?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun setTargetWeight(kg: Double?): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override fun hasProvidedFullName(): Boolean = false

    override suspend fun savePreferences(
        displayName: String,
        biometrics: Biometrics,
        goals: NutritionGoals,
        weight: WeightEntry?,
        warmUpIncluded: Boolean,
        previousValuesMode: PreviousValuesMode,
        calculators: CalculatorSettings?,
        foodPreferences: FoodPreferences?,
    ): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}

/** Weight counterpart of [UnconfiguredAuthRepository]: nothing can be read or saved. */
class UnconfiguredBodyMeasurementRepository : BodyMeasurementRepository {
    override suspend fun getOnboardingWeight(): Result<BodyMeasurement?> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun getLatestWeight(): Result<BodyMeasurement?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun getMeasurements(from: Instant, toExclusive: Instant): Result<List<BodyMeasurement>> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun getLatestComposition(): Result<LatestComposition> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun saveManualWeight(save: ManualWeightSave): Result<String> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun deleteMeasurement(id: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun setHcRecordId(id: String, hcRecordId: String?): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))
}

/** Goals counterpart of [UnconfiguredAuthRepository]: nothing can be read or saved. */
class UnconfiguredNutritionGoalsRepository : NutritionGoalsRepository {
    override suspend fun getOwn(): Result<NutritionGoals?> = Result.failure(AppException(AppError.Unknown))

    override suspend fun upsert(goals: NutritionGoals): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}

/** Account counterpart of [UnconfiguredAuthRepository]: there is no server to delete from. */
class UnconfiguredAccountRepository : AccountRepository {
    override suspend fun deleteAccount(): DeleteAccountOutcome = DeleteAccountOutcome.Failed

    override suspend fun isProtected(): Result<Boolean> = Result.failure(AppException(AppError.Unknown))
}

/** Exercise counterpart of [UnconfiguredAuthRepository]: there is no catalog to read. */
class UnconfiguredExerciseRepository : ExerciseRepository {
    override suspend fun getAll(): Result<List<Exercise>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun getById(id: String): Result<Exercise> = Result.failure(AppException(AppError.Unknown))

    override suspend fun createCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        metricType: ExerciseMetricType,
        perHand: Boolean,
    ): Result<Exercise> = Result.failure(AppException(AppError.Unknown))

    override suspend fun updateCustom(
        id: String,
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        perHand: Boolean,
    ): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun deleteCustom(id: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}

/** Routine counterpart of [UnconfiguredAuthRepository]: there is no server to read or write. */
@Suppress("TooManyFunctions") // One failing stub per repository operation.
class UnconfiguredRoutineRepository : RoutineRepository {
    override suspend fun listActive(): Result<List<RoutineSummary>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun get(id: String): Result<Routine> = Result.failure(AppException(AppError.Unknown))

    override suspend fun save(routine: Routine): Result<String> = Result.failure(AppException(AppError.Unknown))

    override suspend fun duplicate(id: String): Result<String> = Result.failure(AppException(AppError.Unknown))

    override suspend fun updateFromSession(sessionId: String): Result<String> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun createFromSession(sessionId: String, name: String): Result<String> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun archive(id: String): Result<Unit> = Result.failure(AppException(AppError.Unknown))

    override suspend fun listFolders(): Result<List<RoutineFolder>> = Result.failure(AppException(AppError.Unknown))

    override suspend fun createFolder(name: String): Result<String> = Result.failure(AppException(AppError.Unknown))

    override suspend fun renameFolder(id: String, name: String): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun deleteFolder(id: String): Result<Int> = Result.failure(AppException(AppError.Unknown))

    override suspend fun moveToFolder(routineId: String, folderId: String?): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun saveOrder(folderId: String?, orderedIds: List<String>): Result<Unit> =
        Result.failure(AppException(AppError.Unknown))

    override suspend fun setNext(routineId: String?): Result<Unit> = Result.failure(AppException(AppError.Unknown))
}
