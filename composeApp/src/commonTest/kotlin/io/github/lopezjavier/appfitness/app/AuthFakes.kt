package io.github.lopezjavier.appfitness.app

import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

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
}

/** Test double of [AuthCredentialProvider]. */
internal class FakeCredentialProvider(var result: GoogleCredentialResult) : AuthCredentialProvider {
    var calls = 0

    override suspend fun requestGoogleIdToken(): GoogleCredentialResult {
        calls++
        return result
    }
}
