package io.github.lopezjavier.appfitness.domain.repository

import io.github.lopezjavier.appfitness.domain.model.DeleteAccountOutcome

interface AccountRepository {
    /**
     * Asks the server to delete the signed-in user and all their data. Never throws. [DeleteAccountOutcome.Deleted]
     * also covers an already-gone user (401); every other failure leaves the account untouched.
     */
    suspend fun deleteAccount(): DeleteAccountOutcome

    /** Whether the server protects this account from deletion (ADR-57). Never throws except cancellation. */
    suspend fun isProtected(): Result<Boolean>
}
