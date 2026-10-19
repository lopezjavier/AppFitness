package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository
import io.github.lopezjavier.appfitness.domain.repository.AuthRepository

/** Deletes the account server-side and, only when that succeeded, clears the local session (root shows Welcome). */
class DeleteAccount(private val accountRepository: AccountRepository, private val authRepository: AuthRepository) {
    suspend operator fun invoke(): DeleteAccountOutcome {
        val outcome = accountRepository.deleteAccount()
        if (outcome == DeleteAccountOutcome.Deleted) authRepository.signOut()
        return outcome
    }
}
