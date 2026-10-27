package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.model.FoodPreferences
import io.github.lopezjavier.appfitness.domain.model.LatestComposition
import io.github.lopezjavier.appfitness.domain.model.ManualWeightSave
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.NutritionGoals
import io.github.lopezjavier.appfitness.domain.model.PreviousValuesMode
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WeightEntry
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Test double of [AuthRepository]: [state] is the session; sign-ins return [outcome], optionally after [gate]. */
internal class FakeAuthRepository(initial: AuthState = AuthState.Loading) : AuthRepository {
    val state = MutableStateFlow(initial)
    override val authState: Flow<AuthState> = state

    var outcome: SignInOutcome = SignInOutcome.Success
    var gate: CompletableDeferred<Unit>? = null
    val googleCalls = mutableListOf<Pair<String, String>>()
    val passwordCalls = mutableListOf<TestLoginCredentials>()
    var signOutCalls = 0

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): SignInOutcome {
        googleCalls += idToken to rawNonce
        gate?.await()
        return outcome
    }

    override suspend fun signInWithPassword(credentials: TestLoginCredentials): SignInOutcome {
        passwordCalls += credentials
        gate?.await()
        return outcome
    }

    override suspend fun signOut() {
        signOutCalls++
        state.value = AuthState.SignedOut
    }
}

/** One recorded call of [FakeProfileRepository.savePreferences]. */
internal data class SavedPreferences(
    val displayName: String,
    val biometrics: Biometrics,
    val goals: NutritionGoals,
    val weight: WeightEntry?,
    val warmUpIncluded: Boolean? = null,
    val previousValuesMode: PreviousValuesMode? = null,
    val calculators: CalculatorSettings? = null,
    val foodPreferences: FoodPreferences? = null,
    val autoUpdateRoutine: Boolean? = null,
)

/** Test double of [ProfileRepository]: each read returns the next of [results] (the last one repeats). */
internal class FakeProfileRepository(results: List<Result<ProfileSummary>>) : ProfileRepository {
    constructor(single: Result<ProfileSummary>) : this(listOf(single))

    private val queue = results.toMutableList()
    var reads = 0

    /** When set, every [getOwnProfile] returns it instead of the queued results. */
    var profileOverride: Result<ProfileSummary>? = null

    override suspend fun getOwnProfile(): Result<ProfileSummary> {
        reads++
        profileOverride?.let { return it }
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }

    /** Result of [getBiometrics]; [updateResult] is returned by every [updateBiometrics], which also records it. */
    var biometrics: Result<SavedBiometrics> = Result.success(SavedBiometrics())
    var updateResult: Result<Unit> = Result.success(Unit)
    val biometricsUpdates = mutableListOf<Biometrics>()
    var biometricsReads = 0

    override suspend fun getBiometrics(): Result<SavedBiometrics> {
        biometricsReads++
        return biometrics
    }

    override suspend fun updateBiometrics(biometrics: Biometrics): Result<Unit> {
        biometricsUpdates += biometrics
        return updateResult
    }

    /** Result of [getTargetWeight]; saves go to [targetWeightSaves] and answered by [targetWeightSaveResult]. */
    var targetWeight: Result<Double?> = Result.success(null)
    var targetWeightSaveResult: Result<Unit> = Result.success(Unit)
    val targetWeightSaves = mutableListOf<Double?>()

    override suspend fun getTargetWeight(): Result<Double?> = targetWeight

    override suspend fun setTargetWeight(kg: Double?): Result<Unit> {
        targetWeightSaves += kg
        return targetWeightSaveResult
    }

    /** Result of every [savePreferences], which also records the arguments. */
    var settingsResult: Result<Unit> = Result.success(Unit)
    val preferenceSaves = mutableListOf<SavedPreferences>()

    override suspend fun savePreferences(
        displayName: String,
        biometrics: Biometrics,
        goals: NutritionGoals,
        weight: WeightEntry?,
        warmUpIncluded: Boolean?,
        previousValuesMode: PreviousValuesMode?,
        calculators: CalculatorSettings?,
        foodPreferences: FoodPreferences?,
        autoUpdateRoutine: Boolean?,
    ): Result<Unit> {
        preferenceSaves += SavedPreferences(
            displayName,
            biometrics,
            goals,
            weight,
            warmUpIncluded,
            previousValuesMode,
            calculators,
            foodPreferences,
            autoUpdateRoutine,
        )
        return settingsResult
    }

    /** Result of every [updateDisplayName], which also records the names. */
    var displayNameResult: Result<Unit> = Result.success(Unit)
    val displayNameUpdates = mutableListOf<String>()

    override suspend fun updateDisplayName(name: String): Result<Unit> {
        displayNameUpdates += name
        return displayNameResult
    }

    /** What [hasProvidedFullName] answers (true = Google gave a name). */
    var providedFullName: Boolean = true

    override fun hasProvidedFullName(): Boolean = providedFullName

    /** Result of every [markOnboardingCompleted], which also counts the calls. */
    var completedResult: Result<Unit> = Result.success(Unit)
    var completedCalls = 0

    override suspend fun markOnboardingCompleted(): Result<Unit> {
        completedCalls++
        return completedResult
    }
}

/** Test double of [NutritionGoalsRepository]: [row] is what [getOwn] returns; [upsertResult] answers every upsert. */
internal class FakeNutritionGoalsRepository(var row: NutritionGoals? = null) : NutritionGoalsRepository {
    var readResult: Result<NutritionGoals?>? = null
    var upsertResult: Result<Unit> = Result.success(Unit)
    val upserts = mutableListOf<NutritionGoals>()

    override suspend fun getOwn(): Result<NutritionGoals?> = readResult ?: Result.success(row)

    override suspend fun upsert(goals: NutritionGoals): Result<Unit> {
        upserts += goals
        return upsertResult
    }
}

/** Test double of [AuthCredentialProvider]. */
internal class FakeCredentialProvider(var result: GoogleCredentialResult) : AuthCredentialProvider {
    var calls = 0

    override suspend fun requestGoogleIdToken(): GoogleCredentialResult {
        calls++
        return result
    }
}

/**
 * Test double of [BodyMeasurementRepository] with one onboarding row: [row] is null until the first save, which
 * records an insert; later saves record an update of that row (as the real repository does).
 */
internal class FakeBodyMeasurementRepository(var row: BodyMeasurement? = null) : BodyMeasurementRepository {
    var readResult: Result<BodyMeasurement?>? = null
    var saveFailure: Throwable? = null
    val inserts = mutableListOf<Pair<Double, Instant>>()
    val updates = mutableListOf<Triple<String, Double, Instant>>()
    var reads = 0

    /** When set, [getMeasurements] serves these rows (filtered by range, newest first) instead of [row]. */
    var rows: List<BodyMeasurement>? = null

    override suspend fun getOnboardingWeight(): Result<BodyMeasurement?> {
        reads++
        return readResult ?: Result.success(row)
    }

    override suspend fun saveOnboardingWeight(weightKg: Double, measuredAt: Instant): Result<Unit> {
        saveFailure?.let { return Result.failure(it) }
        val existing = row
        if (existing == null) {
            inserts += weightKg to measuredAt
            row = BodyMeasurement("row-1", measuredAt, weightKg, MeasurementSource.Manual)
        } else {
            updates += Triple(existing.id, weightKg, measuredAt)
            row = existing.copy(weightKg = weightKg, measuredAt = measuredAt)
        }
        return Result.success(Unit)
    }

    override suspend fun getLatestWeight(): Result<BodyMeasurement?> = Result.success(row)

    override suspend fun getMeasurements(from: Instant, toExclusive: Instant): Result<List<BodyMeasurement>> =
        Result.success(
            rows?.filter { it.measuredAt >= from && it.measuredAt < toExclusive }?.sortedByDescending { it.measuredAt }
                ?: listOfNotNull(row),
        )

    override suspend fun getLatestComposition(): Result<LatestComposition> = Result.success(LatestComposition())

    override suspend fun saveManualWeight(save: ManualWeightSave): Result<String> = Result.success("row-1")

    override suspend fun deleteMeasurement(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun setHcRecordId(id: String, hcRecordId: String?): Result<Unit> = Result.success(Unit)
}

/** Fixed [Clock]. */
internal class FakeClock(
    private val today: LocalDate = LocalDate(2026, 10, 14),
    private val now: Instant = Instant.parse("2026-10-14T12:00:00Z"),
) : Clock {
    override fun today(): LocalDate = today

    override fun now(): Instant = now
}

/** Test double of [AccountRepository]: every [deleteAccount] returns [outcome], optionally after [gate]. */
internal class FakeAccountRepository(var outcome: DeleteAccountOutcome = DeleteAccountOutcome.Deleted) :
    AccountRepository {
    var gate: CompletableDeferred<Unit>? = null
    var calls = 0
    var protection: Result<Boolean> = Result.success(false)
    var protectionChecks = 0

    override suspend fun isProtected(): Result<Boolean> {
        protectionChecks++
        return protection
    }

    override suspend fun deleteAccount(): DeleteAccountOutcome {
        calls++
        gate?.await()
        return outcome
    }
}

/** Test double of [TrainingPreferencesRepository]: [result] answers every read and [reads] counts them. */
internal class FakeTrainingPreferencesRepository(warmUpIncluded: Boolean = false, autoUpdateRoutine: Boolean = false) :
    TrainingPreferencesRepository {
    var result: Result<TrainingPreferences> =
        Result.success(TrainingPreferences(warmUpIncluded, autoUpdateRoutine = autoUpdateRoutine))
    var reads = 0

    override suspend fun get(): Result<TrainingPreferences> {
        reads++
        return result
    }
}
