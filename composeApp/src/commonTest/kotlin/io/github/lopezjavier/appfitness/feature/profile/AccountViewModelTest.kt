package io.github.lopezjavier.appfitness.feature.profile

import app.cash.turbine.test
import io.github.lopezjavier.appfitness.app.FakeAccountRepository
import io.github.lopezjavier.appfitness.app.FakeAuthRepository
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
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
import kotlin.test.assertTrue

/** Sign out and delete account (US-05, ADR-29, ADR-57), moved from the Perfil root to Preferencias > Cuenta. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class AccountViewModelTest {
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

    private fun viewModel() = AccountViewModel(
        authRepository = auth,
        deleteAccount = DeleteAccount(account, auth),
        getAccountProtection = GetAccountProtection(account),
        connectivity = connectivity,
    )

    private fun TestScope.ready(): AccountViewModel = viewModel().also { advanceUntilIdle() }

    @Test
    fun signOutConfirmed_signsOutOnce() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(AccountEvent.SignOutConfirmed)
        advanceUntilIdle()

        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun deleteConfirmed_deletesAndSignsOut() = runTest {
        val viewModel = ready()

        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(1, account.calls)
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun whileDeleting_isDeletingIsTrue_andASecondRequestIsIgnored() = runTest {
        account.gate = CompletableDeferred()
        val viewModel = ready()

        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.canDelete)
        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(1, account.calls)
        account.gate?.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun deleteFailure_keepsTheSession_andEmitsTheError() = runTest {
        account.outcome = DeleteAccountOutcome.Failed
        val viewModel = ready()
        viewModel.effects.test {
            viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
            advanceUntilIdle()
            assertEquals(AccountEffect.DeleteFailed(offline = false), awaitItem())
        }
        assertEquals(0, auth.signOutCalls)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(viewModel.uiState.value.canDelete)
    }

    @Test
    fun deleteWithoutNetwork_isReportedAsOffline() = runTest {
        account.outcome = DeleteAccountOutcome.NoNetwork
        val viewModel = ready()
        viewModel.effects.test {
            viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
            advanceUntilIdle()
            assertEquals(AccountEffect.DeleteFailed(offline = true), awaitItem())
        }
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun offline_disablesDelete_andIgnoresTheRequest() = runTest {
        val viewModel = ready()
        connectivity.state.value = false
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canDelete)
        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(0, account.calls)
    }

    @Test
    fun protectedAccount_cannotBeDeleted_andTheRequestIsIgnored() = runTest {
        account.protection = Result.success(true)
        val viewModel = ready()

        assertEquals(AccountProtection.Protected, viewModel.uiState.value.protection)
        assertFalse(viewModel.uiState.value.canDelete)
        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(0, account.calls)
    }

    @Test
    fun unprotectedAccount_online_canBeDeleted() = runTest {
        val viewModel = ready()

        assertEquals(AccountProtection.Unprotected, viewModel.uiState.value.protection)
        assertTrue(viewModel.uiState.value.canDelete)
    }

    @Test
    fun beforeTheProtectionCheckFinishes_deleteIsNotEnabled() = runTest {
        val viewModel = viewModel()

        assertEquals(AccountProtection.Unknown, viewModel.uiState.value.protection)
        assertFalse(viewModel.uiState.value.canDelete)
    }

    @Test
    fun failedProtectionCheck_isUnknown_andDeleteStaysDisabled() = runTest {
        account.protection = Result.failure(AppException(AppError.Network))
        val viewModel = ready()

        assertEquals(AccountProtection.Unknown, viewModel.uiState.value.protection)
        assertFalse(viewModel.uiState.value.canDelete)
        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()
        assertEquals(0, account.calls)
    }

    @Test
    fun unknownProtection_isCheckedAgainWhenConnectivityReturns() = runTest {
        account.protection = Result.failure(AppException(AppError.Network))
        val viewModel = ready()
        connectivity.state.value = false
        advanceUntilIdle()

        account.protection = Result.success(false)
        connectivity.state.value = true
        advanceUntilIdle()

        assertEquals(AccountProtection.Unprotected, viewModel.uiState.value.protection)
        assertTrue(viewModel.uiState.value.canDelete)
    }

    @Test
    fun knownProtection_isNotCheckedAgainWhenConnectivityReturns() = runTest {
        ready()
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
        val viewModel = ready()

        viewModel.effects.test {
            viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(AccountProtection.Protected, viewModel.uiState.value.protection)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertFalse(viewModel.uiState.value.canDelete)
        assertEquals(0, auth.signOutCalls)
    }

    @Test
    fun recreatedViewModel_recomputesProtection_andReplaysNoEffect() = runTest {
        account.protection = Result.failure(AppException(AppError.Network))
        assertEquals(AccountProtection.Unknown, ready().uiState.value.protection)

        account.protection = Result.success(true)
        val recreated = ready()

        assertEquals(AccountProtection.Protected, recreated.uiState.value.protection)
        recreated.effects.test { expectNoEvents() }
    }

    @Test
    fun deleted_endsTheSession_soTheAppShowsWelcome() = runTest {
        auth.state.value = AuthState.SignedIn("user-1")
        val viewModel = ready()

        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(AuthState.SignedOut, auth.state.value)
    }

    @Test
    fun deleteFailed_keepsTheUserSignedIn() = runTest {
        account.outcome = DeleteAccountOutcome.Failed
        auth.state.value = AuthState.SignedIn("user-1")
        val viewModel = ready()

        viewModel.onEvent(AccountEvent.DeleteAccountConfirmed)
        advanceUntilIdle()

        assertEquals(AuthState.SignedIn("user-1"), auth.state.value)
    }
}
