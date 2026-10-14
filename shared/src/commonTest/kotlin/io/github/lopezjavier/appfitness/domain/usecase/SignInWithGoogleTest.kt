package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.AuthCredentialProvider
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeConnectivity(online: Boolean) : ConnectivityObserver {
    override val isOnline: Flow<Boolean> = flowOf(online)
}

private class FakeProvider(private val result: GoogleCredentialResult) : AuthCredentialProvider {
    var calls = 0

    override suspend fun requestGoogleIdToken(): GoogleCredentialResult {
        calls++
        return result
    }
}

private class FakeAuthRepository(private val outcome: SignInOutcome) : AuthRepository {
    var receivedIdToken: String? = null
    var receivedNonce: String? = null
    var calls = 0

    override val authState: Flow<AuthState> = flowOf(AuthState.SignedOut)

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String): SignInOutcome {
        calls++
        receivedIdToken = idToken
        receivedNonce = rawNonce
        return outcome
    }

    override suspend fun signInWithPassword(credentials: TestLoginCredentials): SignInOutcome = outcome

    override suspend fun signOut() = Unit
}

class SignInWithGoogleTest {
    private fun useCase(
        online: Boolean = true,
        provider: FakeProvider = FakeProvider(GoogleCredentialResult.Token("id-token", "raw-nonce")),
        repository: FakeAuthRepository = FakeAuthRepository(SignInOutcome.Success),
    ) = SignInWithGoogle(FakeConnectivity(online), provider, repository)

    @Test
    fun offline_returnsNoNetworkWithoutOpeningThePicker() = runTest {
        val provider = FakeProvider(GoogleCredentialResult.Token("t", "n"))
        val repository = FakeAuthRepository(SignInOutcome.Success)

        val outcome = useCase(online = false, provider = provider, repository = repository)()

        assertEquals(SignInOutcome.NoNetwork, outcome)
        assertEquals(0, provider.calls)
        assertEquals(0, repository.calls)
    }

    @Test
    fun pickerCancelled_returnsCancelledAndSkipsTheRepository() = runTest {
        val repository = FakeAuthRepository(SignInOutcome.Success)

        val outcome = useCase(provider = FakeProvider(GoogleCredentialResult.Cancelled), repository = repository)()

        assertEquals(SignInOutcome.Cancelled, outcome)
        assertEquals(0, repository.calls)
    }

    @Test
    fun providerNoNetwork_propagates() = runTest {
        val outcome = useCase(provider = FakeProvider(GoogleCredentialResult.NoNetwork))()

        assertEquals(SignInOutcome.NoNetwork, outcome)
    }

    @Test
    fun providerFailed_returnsFailed() = runTest {
        val outcome = useCase(provider = FakeProvider(GoogleCredentialResult.Failed(IllegalStateException("boom"))))()

        assertEquals(SignInOutcome.Failed, outcome)
    }

    @Test
    fun token_isForwardedWithItsRawNonce() = runTest {
        val repository = FakeAuthRepository(SignInOutcome.Success)

        val outcome = useCase(repository = repository)()

        assertEquals(SignInOutcome.Success, outcome)
        assertEquals("id-token", repository.receivedIdToken)
        assertEquals("raw-nonce", repository.receivedNonce)
    }

    @Test
    fun repositoryNoNetwork_returnsNoNetwork() = runTest {
        val outcome = useCase(repository = FakeAuthRepository(SignInOutcome.NoNetwork))()

        assertEquals(SignInOutcome.NoNetwork, outcome)
    }
}
