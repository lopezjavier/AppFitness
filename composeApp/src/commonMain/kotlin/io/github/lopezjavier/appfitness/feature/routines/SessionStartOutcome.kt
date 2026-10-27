package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull

/** What starting a session from a routine did; shared by the routine list and the routine details. */
internal enum class SessionStartOutcome { Started, AlreadyInProgress, Failed }

internal fun Result<String>.toStartOutcome(): SessionStartOutcome = when {
    isSuccess -> SessionStartOutcome.Started
    appErrorOrNull() == AppError.SessionAlreadyInProgress -> SessionStartOutcome.AlreadyInProgress
    else -> SessionStartOutcome.Failed
}
