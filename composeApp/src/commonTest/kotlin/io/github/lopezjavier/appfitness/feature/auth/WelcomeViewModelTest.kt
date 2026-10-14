package io.github.lopezjavier.appfitness.feature.auth

import io.github.lopezjavier.appfitness.app.FakeAuthRepository
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeCredentialProvider
import io.github.lopezjavier.appfitness.config.AppConfig
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.port.GoogleCredentialResult
import io.github.lopezjavier.appfitness.domain.usecase.SignInWithGoogle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val credentials = TestLoginCredentials("test@appfitness.local", "pw")
    private val connectivity = FakeConnectivityObserver(initial = true)
    private val provider = FakeCredentialProvider(GoogleCredentialResult.Token("id-token", "raw-nonce"))
    private val auth = FakeAuthRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(testLogin: TestLoginCredentials? = credentials) = WelcomeViewModel(
        signInWithGoogle = SignInWithGoogle(connectivity, provider, auth),
        authRepository = auth,
        config = AppConfig("http://x", "sb_publishable_x", "", isLocal = true, testLogin = testLogin),
    )

    @Test
    fun firstSignIn_success_clearsInFlightWithoutError() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        assertEquals(WelcomeUiState(inFlight = true), viewModel.uiState.value)
        runCurrent()

        assertEquals(WelcomeUiState(), viewModel.uiState.value)
        assertEquals(listOf("id-token" to "raw-nonce"), auth.googleCalls)
    }

    @Test
    fun pickerCancelled_showsNoError() = runTest {
        provider.result = GoogleCredentialResult.Cancelled
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        runCurrent()

        assertEquals(WelcomeUiState(), viewModel.uiState.value)
        assertEquals(0, auth.googleCalls.size)
    }

    @Test
    fun offline_showsNoNetworkError_andTappingGoogleAgainRepeatsThePath() = runTest {
        connectivity.state.value = false
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        runCurrent()
        assertEquals(WelcomeUiState(error = WelcomeError.NoNetwork), viewModel.uiState.value)
        assertEquals(0, provider.calls)

        connectivity.state.value = true
        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        runCurrent()

        assertEquals(WelcomeUiState(), viewModel.uiState.value)
        assertEquals(1, auth.googleCalls.size)
    }

    @Test
    fun genericFailure_showsGenericError() = runTest {
        provider.result = GoogleCredentialResult.Failed()
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        runCurrent()

        assertEquals(WelcomeUiState(error = WelcomeError.Generic), viewModel.uiState.value)
    }

    @Test
    fun testLogin_usesTheConfiguredCredentials_andTappingItAgainRepeatsIt() = runTest {
        auth.outcome = SignInOutcome.NoNetwork
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.TestLogin)
        runCurrent()
        assertEquals(WelcomeUiState(error = WelcomeError.NoNetwork), viewModel.uiState.value)

        auth.outcome = SignInOutcome.Success
        viewModel.onEvent(WelcomeEvent.TestLogin)
        runCurrent()

        assertEquals(listOf(credentials, credentials), auth.passwordCalls)
        assertEquals(0, auth.googleCalls.size)
        assertEquals(WelcomeUiState(), viewModel.uiState.value)
    }

    @Test
    fun testLogin_withoutConfiguredCredentials_failsClosed() = runTest {
        val viewModel = viewModel(testLogin = null)

        viewModel.onEvent(WelcomeEvent.TestLogin)
        runCurrent()

        assertEquals(WelcomeUiState(error = WelcomeError.Generic), viewModel.uiState.value)
        assertEquals(0, auth.passwordCalls.size)
    }

    @Test
    fun secondTapWhileInFlight_isIgnored() = runTest {
        auth.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        runCurrent()
        viewModel.onEvent(WelcomeEvent.ContinueWithGoogle)
        viewModel.onEvent(WelcomeEvent.TestLogin)
        runCurrent()

        assertEquals(true, viewModel.uiState.value.inFlight)
        assertEquals(1, auth.googleCalls.size)
        assertEquals(0, auth.passwordCalls.size)
        auth.gate?.complete(Unit)
        runCurrent()
        assertEquals(false, viewModel.uiState.value.inFlight)
    }
}
