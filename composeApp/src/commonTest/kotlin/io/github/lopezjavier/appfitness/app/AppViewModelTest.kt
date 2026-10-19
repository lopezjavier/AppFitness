package io.github.lopezjavier.appfitness.app

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val onboarded = Result.success(ProfileSummary("Ana", "https://img/ana.png", onboardingCompleted = true))
    private val notOnboarded = Result.success(ProfileSummary("Ana", null, onboardingCompleted = false))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        connectivity: FakeConnectivityObserver = FakeConnectivityObserver(initial = true),
        auth: FakeAuthRepository = FakeAuthRepository(),
        profile: FakeProfileRepository = FakeProfileRepository(onboarded),
    ) = AppViewModel(connectivity, auth, profile)

    @Test
    fun onlineOfflineOnline_togglesIsOffline() = runTest {
        val fake = FakeConnectivityObserver(initial = true)
        val viewModel = viewModel(connectivity = fake)
        viewModel.uiState.test {
            assertEquals(false, awaitItem().isOffline)
            fake.state.value = false
            assertEquals(true, awaitItem().isOffline)
            fake.state.value = true
            assertEquals(false, awaitItem().isOffline)
        }
    }

    @Test
    fun startsOffline_emitsOfflineAfterInitial() = runTest {
        val viewModel = viewModel(connectivity = FakeConnectivityObserver(initial = false))
        viewModel.uiState.test {
            assertEquals(AppUiState(), awaitItem())
            assertEquals(AppUiState(isOffline = true), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun repeatedSameState_noDuplicateEmission() = runTest {
        val fake = FakeConnectivityObserver(initial = true)
        val viewModel = viewModel(connectivity = fake)
        viewModel.uiState.test {
            assertEquals(false, awaitItem().isOffline)
            fake.state.value = true
            fake.state.value = true
            expectNoEvents()
        }
    }

    @Test
    fun noSubscribers_stopsCollectingUpstream() = runTest {
        val fake = FakeConnectivityObserver(initial = true)
        val viewModel = viewModel(connectivity = fake)
        viewModel.uiState.test {
            awaitItem()
            runCurrent()
            assertEquals(1, fake.state.subscriptionCount.value)
            cancelAndIgnoreRemainingEvents()
        }
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, fake.state.subscriptionCount.value)
    }

    @Test
    fun initialState_isLoading() = runTest {
        viewModel().uiState.test {
            assertEquals(RootRoute.Loading, awaitItem().rootRoute)
        }
    }

    @Test
    fun signedOut_isWelcome() = runTest {
        val viewModel = viewModel(auth = FakeAuthRepository(AuthState.SignedOut))
        viewModel.uiState.test {
            awaitItem()
            assertEquals(RootRoute.Welcome, awaitItem().rootRoute)
        }
    }

    @Test
    fun signedInOnboarded_isMain() = runTest {
        val viewModel = viewModel(auth = FakeAuthRepository(AuthState.SignedIn("u1")))
        viewModel.uiState.test {
            awaitItem()
            val state = awaitItem()
            assertEquals(RootRoute.Main, state.rootRoute)
        }
    }

    @Test
    fun signedInNotOnboarded_isOnboarding() = runTest {
        val viewModel =
            viewModel(
                auth = FakeAuthRepository(AuthState.SignedIn("u1")),
                profile = FakeProfileRepository(notOnboarded),
            )
        viewModel.uiState.test {
            awaitItem()
            assertEquals(RootRoute.Onboarding, awaitItem().rootRoute)
        }
    }

    @Test
    fun onboardingCompleted_routesToMainWithoutReadingTheProfileAgain() = runTest {
        val profile = FakeProfileRepository(notOnboarded)
        val viewModel = viewModel(auth = FakeAuthRepository(AuthState.SignedIn("u1")), profile = profile)
        viewModel.uiState.test {
            awaitItem()
            assertEquals(RootRoute.Onboarding, awaitItem().rootRoute)

            viewModel.onEvent(AppEvent.OnboardingCompleted)

            assertEquals(RootRoute.Main, awaitItem().rootRoute)
            expectNoEvents()
        }
        assertEquals(1, profile.reads)
    }

    @Test
    fun onboardingCompleted_isForgottenWhenTheSessionChanges() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedIn("u1"))
        val viewModel = viewModel(auth = auth, profile = FakeProfileRepository(notOnboarded))
        viewModel.uiState.test {
            awaitItem()
            assertEquals(RootRoute.Onboarding, awaitItem().rootRoute)
            viewModel.onEvent(AppEvent.OnboardingCompleted)
            assertEquals(RootRoute.Main, awaitItem().rootRoute)

            auth.state.value = AuthState.SignedOut
            assertEquals(RootRoute.Welcome, awaitItem().rootRoute)
            auth.state.value = AuthState.SignedIn("u2")
            var route: RootRoute
            do {
                route = awaitItem().rootRoute
            } while (route == RootRoute.Loading)
            assertEquals(RootRoute.Onboarding, route)
        }
    }

    @Test
    fun profileFailure_isProfileError_thenRetryLoadsMain() = runTest {
        val failure = Result.failure<ProfileSummary>(AppException(AppError.Network))
        val profile = FakeProfileRepository(listOf(failure, onboarded))
        val viewModel = viewModel(auth = FakeAuthRepository(AuthState.SignedIn("u1")), profile = profile)
        viewModel.uiState.test {
            awaitItem()
            assertEquals(RootRoute.ProfileError, awaitItem().rootRoute)
            viewModel.onEvent(AppEvent.RetryProfile)
            assertEquals(RootRoute.Loading, awaitItem().rootRoute)
            assertEquals(RootRoute.Main, awaitItem().rootRoute)
        }
        assertEquals(2, profile.reads)
    }

    @Test
    fun signOut_returnsToWelcome() = runTest {
        val auth = FakeAuthRepository(AuthState.SignedIn("u1"))
        val viewModel = viewModel(auth = auth)
        viewModel.uiState.test {
            awaitItem()
            assertEquals(RootRoute.Main, awaitItem().rootRoute)
            auth.state.value = AuthState.SignedOut
            val state = awaitItem()
            assertEquals(RootRoute.Welcome, state.rootRoute)
        }
    }
}
