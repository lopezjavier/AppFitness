package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAccountProtectionTest {
    private class Account(var result: Result<Boolean>) : AccountRepository {
        override suspend fun deleteAccount() = DeleteAccountOutcome.Failed

        override suspend fun isProtected() = result
    }

    @Test
    fun protectedAccount_isProtected() = runTest {
        assertEquals(AccountProtection.Protected, GetAccountProtection(Account(Result.success(true)))())
    }

    @Test
    fun unprotectedAccount_isUnprotected() = runTest {
        assertEquals(AccountProtection.Unprotected, GetAccountProtection(Account(Result.success(false)))())
    }

    @Test
    fun failedCheck_isUnknown() = runTest {
        val account = Account(Result.failure(IllegalStateException("offline")))

        assertEquals(AccountProtection.Unknown, GetAccountProtection(account)())
    }
}
