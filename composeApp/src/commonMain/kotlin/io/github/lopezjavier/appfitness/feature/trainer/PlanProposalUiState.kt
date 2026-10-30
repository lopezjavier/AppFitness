package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan

/** Why a plan could not be shown; the UI picks the Spanish message and whether "Reintentar" makes sense. */
sealed interface PlanFailureKind {
    /** The monthly budget ran out; [scope] says whose (user or global). No proposal is shown. */
    data class Limit(val scope: CoachFailure.Scope) : PlanFailureKind

    data object NoCredit : PlanFailureKind

    data object Timeout : PlanFailureKind

    data object Invalid : PlanFailureKind

    /** The user lacks data the plan needs. */
    data object Missing : PlanFailureKind

    /** No safe plan fits the user's numbers. */
    data object Infeasible : PlanFailureKind

    data object Offline : PlanFailureKind

    /** The screen was recreated mid-request and no plan was stored: the call may have been cut. */
    data object Interrupted : PlanFailureKind

    data object Other : PlanFailureKind
}

/** Maps a failed coach call to what the screen shows; shared by the plan and the block screens. */
internal fun planFailureKindOf(failure: Throwable?): PlanFailureKind = when {
    failure is CoachException -> when (val coachFailure = failure.failure) {
        is CoachFailure.LimitReached -> PlanFailureKind.Limit(coachFailure.scope)
        CoachFailure.NoCredit -> PlanFailureKind.NoCredit
        CoachFailure.Timeout -> PlanFailureKind.Timeout
        CoachFailure.Invalid -> PlanFailureKind.Invalid
        CoachFailure.Missing -> PlanFailureKind.Missing
        CoachFailure.Infeasible -> PlanFailureKind.Infeasible
        CoachFailure.NeedsConfirmation, CoachFailure.Conflict, CoachFailure.Other -> PlanFailureKind.Other
    }

    (failure as? AppException)?.error == AppError.Network -> PlanFailureKind.Offline

    else -> PlanFailureKind.Other
}

/** Why "Aceptar" is disabled; null on [PlanProposalUiState.Proposal] when it is enabled. */
enum class AcceptBlockedReason {
    /** Some derived value is still provisional: confirm it first. */
    Unconfirmed,

    /** The data changed since the user confirmed (re-ask, or the server refused): confirm again. */
    DataChanged,
}

sealed interface PlanProposalUiState {
    /** Reading the stored plan (R-31 skeleton). */
    data object Loading : PlanProposalUiState

    /** No proposed plan: the screen asks the coach once with [PlanProposalEvent.Request]. */
    data object Idle : PlanProposalUiState

    /** Waiting for the model (indeterminate progress). */
    data object Requesting : PlanProposalUiState

    /**
     * A proposed [plan] with the derived [values] behind it. [confirming], [accepting] and [discarding] are true while
     * that call is in flight. [dataChanged] is set when the server refused the plan for unconfirmed values.
     */
    data class Proposal(
        val plan: CoachPlan,
        val values: CoachDerivedValues,
        val confirming: Boolean = false,
        val accepting: Boolean = false,
        val discarding: Boolean = false,
        val dataChanged: Boolean = false,
    ) : PlanProposalUiState {
        val busy: Boolean get() = confirming || accepting || discarding

        /** US-68 gate: nothing provisional and no call in flight. */
        val canAccept: Boolean get() = values.allConfirmed && !busy

        val acceptBlockedReason: AcceptBlockedReason?
            get() = when {
                values.allConfirmed -> null
                dataChanged || values.anyReask -> AcceptBlockedReason.DataChanged
                else -> AcceptBlockedReason.Unconfirmed
            }
    }

    data class Failed(val kind: PlanFailureKind) : PlanProposalUiState

    /** The plan was accepted or discarded; the screen is leaving. */
    data object Closed : PlanProposalUiState
}

enum class PlanAction { Confirm, Accept, Discard }

sealed interface PlanProposalEvent {
    /** Asks the coach for a plan (costs money); ignored unless the state is [PlanProposalUiState.Idle]. */
    data object Request : PlanProposalEvent

    /** Reloads a plan stored meanwhile, else asks the coach again. */
    data object Retry : PlanProposalEvent

    /** "Confirmar" on the derived values card. */
    data object Confirm : PlanProposalEvent

    data object Accept : PlanProposalEvent

    data object Discard : PlanProposalEvent
}

sealed interface PlanProposalEffect {
    /** The derived values were confirmed (confirm haptic). */
    data object ValuesConfirmed : PlanProposalEffect

    /** The plan was accepted and the Diario targets changed: confirm haptic, message, navigate back. */
    data object Accepted : PlanProposalEffect

    /** The plan was discarded: navigate back. */
    data object Discarded : PlanProposalEffect

    data class ActionFailed(val action: PlanAction, val offline: Boolean) : PlanProposalEffect
}
