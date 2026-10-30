package io.github.lopezjavier.appfitness.domain.model

/** Why a `coach` function call was refused or failed, parsed from its error body. */
sealed interface CoachFailure {
    /** `coach_limit_reached`; [scope] says whose budget ran out. */
    data class LimitReached(val scope: Scope) : CoachFailure

    /** `coach_no_credit`: the provider rejected the call for lack of balance. */
    data object NoCredit : CoachFailure

    /** `coach_timeout`. */
    data object Timeout : CoachFailure

    /** Any other or unreadable error. */
    data object Other : CoachFailure

    enum class Scope { User, Global }
}
