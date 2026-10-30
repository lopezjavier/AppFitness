package io.github.lopezjavier.appfitness.domain.model

/** Why a `coach` function call was refused or failed, parsed from its error body. */
sealed interface CoachFailure {
    /** `coach_limit_reached`; [scope] says whose budget ran out. */
    data class LimitReached(val scope: Scope) : CoachFailure

    /** `coach_no_credit`: the provider rejected the call for lack of balance. */
    data object NoCredit : CoachFailure

    /** `coach_timeout`. */
    data object Timeout : CoachFailure

    /** `coach_invalid_output`: the model answered something the server could not validate. */
    data object Invalid : CoachFailure

    /** `coach_missing_data`: the user lacks data the plan needs (sex, weight, expenditure, intention). */
    data object Missing : CoachFailure

    /** `coach_plan_infeasible`: no safe plan fits the user's numbers. */
    data object Infeasible : CoachFailure

    /** Accepting was refused because a derived value is still unconfirmed (SQLSTATE 55000, US-68). */
    data object NeedsConfirmation : CoachFailure

    /** The plan or recommendation is no longer in the state the action needs (SQLSTATE 55000). */
    data object Conflict : CoachFailure

    /** Any other or unreadable error. */
    data object Other : CoachFailure

    enum class Scope { User, Global }
}
