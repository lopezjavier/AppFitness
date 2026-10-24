package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.AccountProtection
import io.github.lopezjavier.appfitness.domain.repository.AccountRepository

/** Reads whether the account is protected from deletion; a failed check is [AccountProtection.Unknown]. */
class GetAccountProtection(private val accountRepository: AccountRepository) {
    suspend operator fun invoke(): AccountProtection = accountRepository.isProtected().fold(
        onSuccess = { if (it) AccountProtection.Protected else AccountProtection.Unprotected },
        onFailure = { AccountProtection.Unknown },
    )
}
