package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.AuthState
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.model.SignInOutcome
import io.github.lopezjavier.appfitness.domain.model.TestLoginCredentials
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteAccountTest {
    private class Account(var outcome: DeleteAccountOutcome) : AccountRepository {
        override suspend fun deleteAccount() = outcome

        override suspend fun isProtected(): Result<Boolean> = Result.success(false)
    }

    private class Auth : AuthRepository {
        var signOuts = 0
        override val authState: Flow<AuthState> = flowOf(AuthState.Loading)

        override suspend fun signInWithGoogle(idToken: String, rawNonce: String) = SignInOutcome.Success

        override suspend fun signInWithPassword(credentials: TestLoginCredentials) = SignInOutcome.Success

        override suspend fun signOut() {
            signOuts++
        }
    }

    @Test
    fun deleted_clearsTheLocalSession() = runTest {
        val auth = Auth()

        val outcome = DeleteAccount(Account(DeleteAccountOutcome.Deleted), auth)()

        assertEquals(DeleteAccountOutcome.Deleted, outcome)
        assertEquals(1, auth.signOuts)
    }

    @Test
    fun anyOtherOutcome_keepsTheSession() = runTest {
        for (failure in listOf(
            DeleteAccountOutcome.NoNetwork,
            DeleteAccountOutcome.Failed,
            DeleteAccountOutcome.Protected,
        )) {
            val auth = Auth()

            val outcome = DeleteAccount(Account(failure), auth)()

            assertEquals(failure, outcome)
            assertEquals(0, auth.signOuts)
        }
    }
}
