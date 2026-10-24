package io.github.lopezjavier.appfitness.feature.profile

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeAccountRepository
import io.github.lopezjavier.appfitness.app.FakeAuthRepository
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeProfileRepository
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.model.ProfileSummary
import io.github.lopezjavier.appfitness.domain.usecase.DeleteAccount
import io.github.lopezjavier.appfitness.domain.usecase.GetAccountProtection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** US-05 on the Perfil root: the name and photo, sign out and delete account. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class ProfileRootViewModelTest {
    private val profile = FakeProfileRepository(Result.success(ProfileSummary("Ana", "https://photo/a.png", true)))
    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository()
    private val connectivity = FakeConnectivityObserver(initial = true)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProfileRootViewModel(
        profileRepository = profile,
        authRepository = auth,
        deleteAccount = DeleteAccount(account, auth),
        getAccountProtection = GetAccountProtection(account),
        connectivity = connectivity,
    )

    private fun TestScope.loaded(): Pair<ProfileRootViewModel, () -> ProfileRootUiState.Loaded> {
        val viewModel = viewModel()
        advanceUntilIdle()
        return viewModel to { assertIs<ProfileRootUiState.Loaded>(viewModel.uiState.value) }
    }

    @Test
    fun loading_showsTheNameAndThePhoto() = runTest {
        val (_, state) = loaded()

        assertEquals("Ana", state().displayName)
        assertEquals("https://photo/a.png", state().avatarUrl)
    }

    @Test
    fun beforeTheReadFinishes_isLoading() = runTest {
        val viewModel = viewModel()

        assertEquals(ProfileRootUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun missingNameAndPhoto_areNull() = runTest {
        profile.profileOverride = Result.success(ProfileSummary(null, null, true))

        val (_, state) = loaded()

        assertNull(state().displayName)
        assertNull(state().avatarUrl)
    }

    @Test
    fun blankName_isShownAsMissing() = runTest {
        profile.profileOverride = Result.success(ProfileSummary("  ", null, true))

        val (_, state) = loaded()

        assertNull(state().displayName)
    }

    @Test
    fun readFailure_isLoadFailed_andRetryReloads() = runTest {
        profile.profileOverride = Result.failure(AppException(AppError.Network))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(ProfileRootUiState.LoadFailed, viewModel.uiState.value)

        profile.profileOverride = null
        viewModel.onEvent(ProfileRootEvent.Retry)
        advanceUntilIdle()

        assertIs<ProfileRootUiState.Loaded>(viewModel.uiState.value)
    }

    @Test
    fun signOutConfirmed_signsOutOnce() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(ProfileRootEvent.SignOutConfirmed)
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun deleteConfirmed_deletesAndSignsOut() = runTest {
        val (viewModel, _) = loaded()

        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(1, account.calls)
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun whileDeleting_isDeletingIsTrue_andASecondRequestIsIgnored() = runTest {
        account.gate = CompletableDeferred()
        val (viewModel, state) = loaded()

        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()
        assertTrue(state().isDeleting)
        assertFalse(state().canDelete)
        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(1, account.calls)
        account.gate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun deleteFailure_keepsTheSession_andEmitsTheError() = runTest {
        account.outcome = DeleteAccountOutcome.Failed
        val (viewModel, state) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
            advanceUntilIdle()
            assertEquals(ProfileRootEffect.DeleteFailed(offline = false), awaitItem())
        }
        assertEquals(0, auth.signOutCalls)
        assertFalse(state().isDeleting)
        assertTrue(state().canDelete)
    }

    @Test
    fun deleteWithoutNetwork_isReportedAsOffline() = runTest {
        account.outcome = DeleteAccountOutcome.NoNetwork
        val (viewModel, _) = loaded()
        viewModel.effects.test {
            viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
            advanceUntilIdle()
            assertEquals(ProfileRootEffect.DeleteFailed(offline = true), awaitItem())
        }
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun offline_disablesDelete_andIgnoresTheRequest() = runTest {
        val (viewModel, state) = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(state().canDelete)
        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(0, account.calls)
    }

    @Test
    fun protectedAccount_cannotBeDeleted_andTheRequestIsIgnored() = runTest {
        account.protection = Result.success(true)
        val (viewModel, state) = loaded()

        assertEquals(AccountProtection.Protected, state().protection)
        assertFalse(state().canDelete)
        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(0, account.calls)
    }

    @Test
    fun unprotectedAccount_online_canBeDeleted() = runTest {
        val (_, state) = loaded()

        assertEquals(AccountProtection.Unprotected, state().protection)
        assertTrue(state().canDelete)
    }

    @Test
    fun failedProtectionCheck_isUnknown_andDeleteStaysDisabled() = runTest {
        account.protection = Result.failure(AppException(AppError.Network))
        val (viewModel, state) = loaded()

        assertEquals(AccountProtection.Unknown, state().protection)
        assertFalse(state().canDelete)
        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()
        assertEquals(0, account.calls)
    }

    @Test
    fun unknownProtection_isCheckedAgainWhenConnectivityReturns() = runTest {
        account.protection = Result.failure(AppException(AppError.Network))
        val (_, state) = loaded()
        connectivity.state.value = false
        advanceUntilIdle()

        account.protection = Result.success(false)
        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(AccountProtection.Unprotected, state().protection)
        assertTrue(state().canDelete)
    }

    @Test
    fun knownProtection_isNotCheckedAgainWhenConnectivityReturns() = runTest {
        val (_, _) = loaded()
        val checks = account.protectionChecks
        connectivity.state.value = false
        advanceUntilIdle()
        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(checks, account.protectionChecks)
    }

    @Test
    fun protectedOutcome_marksTheAccountProtected_withoutAnErrorEffect() = runTest {
        account.outcome = DeleteAccountOutcome.Protected
        val (viewModel, state) = loaded()

        viewModel.effects.test {
            viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(AccountProtection.Protected, state().protection)
        assertFalse(state().isDeleting)
        assertFalse(state().canDelete)
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun recreatedViewModel_recomputesProtection_andReplaysNoEffect() = runTest {
        account.protection = Result.failure(AppException(AppError.Network))
        val (_, firstState) = loaded()
        assertEquals(AccountProtection.Unknown, firstState().protection)

        account.protection = Result.success(true)
        val (recreated, state) = loaded()

        assertEquals(AccountProtection.Protected, state().protection)
        recreated.effects.test { expectNoEvents() }
    }

    @Test
    fun deleted_endsTheSession_soTheRootShowsWelcome() = runTest {
        auth.state.value = AuthState.SignedIn("user-1")
        val (viewModel, _) = loaded()

        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun deleteFailed_keepsTheUserSignedIn() = runTest {
        account.outcome = DeleteAccountOutcome.Failed
        auth.state.value = AuthState.SignedIn("user-1")
        val (viewModel, _) = loaded()

        viewModel.onEvent(ProfileRootEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(AuthState.SignedIn("user-1"), auth.state.value)
    }
}
