package io.github.lopezjavier.appfitness.feature.profile

import io.github.lopezjavier.appfitness.app.FakeAuthRepository
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
class ProfileViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signOutConfirmed_signsOutOnce() = runTest {
        val auth = FakeAuthRepository()
        val viewModel = ProfileViewModel(auth)

        viewModel.onEvent(ProfileEvent.SignOutConfirmed)
        runCurrent()

        assertEquals(1, auth.signOutCalls)
    }
}
