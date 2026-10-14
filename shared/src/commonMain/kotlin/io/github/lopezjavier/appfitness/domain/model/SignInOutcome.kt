package io.github.lopezjavier.appfitness.domain.model

/** Result of a sign-in attempt. [Cancelled] is a silent return to idle, never an error message. */
sealed interface SignInOutcome {
    data object Success : SignInOutcome

    data object Cancelled : SignInOutcome

    data object NoNetwork : SignInOutcome

    data object Failed : SignInOutcome
}
