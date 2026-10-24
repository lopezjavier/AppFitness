package io.github.lopezjavier.appfitness.domain.model

/** Result of asking the server to delete the account. [Protected]: the server refuses (ADR-57). */
enum class DeleteAccountOutcome { Deleted, NoNetwork, Failed, Protected }

/** Whether the signed-in account may be deleted; [Unknown] when the check could not be done (never enables). */
enum class AccountProtection { Protected, Unprotected, Unknown }
