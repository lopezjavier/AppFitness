package io.github.lopezjavier.appfitness.domain.model

/** Carried by a failed `Result` when the `coach` function refused or failed a call; [failure] says why. */
class CoachException(val failure: CoachFailure) : Exception(failure.toString())
