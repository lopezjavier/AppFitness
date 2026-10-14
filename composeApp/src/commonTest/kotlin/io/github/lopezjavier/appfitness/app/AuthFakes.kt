package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.Biometrics
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SavedBiometrics
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
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
    }
}

/** Test double of [ProfileRepository]: each read returns the next of [results] (the last one repeats). */
internal class FakeProfileRepository(results: List<Result<ProfileSummary>>) : ProfileRepository {
    constructor(single: Result<ProfileSummary>) : this(listOf(single))

    private val queue = results.toMutableList()
    var reads = 0

    override suspend fun getOwnProfile(): Result<ProfileSummary> {
        reads++
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
}

/** Fixed [Clock]. */
internal class FakeClock(
    private val today: LocalDate = LocalDate(2026, 10, 14),
    private val now: Instant = Instant.parse("2026-10-14T12:00:00Z"),
) : Clock {
    override fun today(): LocalDate = today

    override fun now(): Instant = now
}
