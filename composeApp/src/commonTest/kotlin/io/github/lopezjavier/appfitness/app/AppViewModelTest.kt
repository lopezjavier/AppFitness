package io.github.lopezjavier.appfitness.app

import app.cash.turbine.test
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
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onlineOfflineOnline_togglesIsOffline() = runTest {
        val fake = FakeConnectivityObserver(initial = true)
        val viewModel = AppViewModel(fake)
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
        val viewModel = AppViewModel(FakeConnectivityObserver(initial = false))
        viewModel.uiState.test {
            assertEquals(AppUiState(), awaitItem())
            assertEquals(AppUiState(isOffline = true), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun repeatedSameState_noDuplicateEmission() = runTest {
        val fake = FakeConnectivityObserver(initial = true)
        val viewModel = AppViewModel(fake)
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
        val viewModel = AppViewModel(fake)
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
}
