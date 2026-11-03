package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.MissingField
import io.github.lopezjavier.appfitness.domain.model.Phase

/** Why a plan could not be shown; the UI picks the Spanish message and whether "Reintentar" makes sense. */
sealed interface PlanFailureKind {
    /** The monthly budget ran out; [scope] says whose (user or global). No proposal is shown. */
    data class Limit(val scope: CoachFailure.Scope) : PlanFailureKind

    data object NoCredit : PlanFailureKind

    data object Timeout : PlanFailureKind

    data object Invalid : PlanFailureKind

    /** The user lacks data the plan needs; [fields] names it (empty when unknown). */
    data class Missing(val fields: Set<MissingField>) : PlanFailureKind

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

        is CoachFailure.Missing -> PlanFailureKind.Missing(coachFailure.fields)

        CoachFailure.Infeasible -> PlanFailureKind.Infeasible

        CoachFailure.NeedsConfirmation,
        CoachFailure.Conflict,
        CoachFailure.InvalidReplacement,
        is CoachFailure.NeedsChanges,
        CoachFailure.Other,
        -> PlanFailureKind.Other
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
     * A proposed [plan]. [unconfirmed] is true while some input the plan reads is still provisional (the re-ask): the
     * user confirms it in "Antes de empezar", which requests the plan again. [accepting] and [discarding] are true
     * while that call is in flight. [dataChanged] is set when the server refused the plan for unconfirmed values.
     */
    data class Proposal(
        val plan: CoachPlan,
        val unconfirmed: Boolean = false,
        val accepting: Boolean = false,
        val discarding: Boolean = false,
        val dataChanged: Boolean = false,
        /** The open cardio-minutes sheet; null when closed or when the plan has no activity targets. */
        val cardioEdit: CardioEdit? = null,
        /** True while the cut rate or the diet break is being stored and the plan recomputed. */
        val optionsSaving: Boolean = false,
    ) : PlanProposalUiState {
        val busy: Boolean get() = accepting || discarding || optionsSaving || cardioEdit?.saving == true

        /** The rate selector and the diet-break switch exist only on a cut plan. */
        val hasCutOptions: Boolean get() = plan.phase == Phase.Cut

        /** US-68 gate: nothing provisional and no call in flight. */
        val canAccept: Boolean get() = !unconfirmed && !busy

        val acceptBlockedReason: AcceptBlockedReason?
            get() = when {
                !unconfirmed -> null
                dataChanged -> AcceptBlockedReason.DataChanged
                else -> AcceptBlockedReason.Unconfirmed
            }
    }

    data class Failed(val kind: PlanFailureKind) : PlanProposalUiState

    /** The plan was accepted or discarded; the screen is leaving. */
    data object Closed : PlanProposalUiState
}

enum class PlanAction { Accept, Discard, Cardio, Options }

sealed interface PlanProposalEvent {
    /** Asks the coach for a plan (costs money); ignored unless the state is [PlanProposalUiState.Idle]. */
    data object Request : PlanProposalEvent

    /** Reloads a plan stored meanwhile, else asks the coach again. */
    data object Retry : PlanProposalEvent

    data object Accept : PlanProposalEvent

    data object Discard : PlanProposalEvent

    /** Opens the cardio-minutes sheet at the saved value; ignored when the plan has no activity targets. */
    data object OpenCardioEdit : PlanProposalEvent

    /** The stepper moved: [minutes] is clamped to 0..600 in steps of 5. The steps target has no edit event. */
    data class CardioMinutesChanged(val minutes: Int) : PlanProposalEvent

    /** "Guardar" in the sheet: stores the draft on the proposed plan; ignored while it equals the saved value. */
    data object SaveCardio : PlanProposalEvent

    /** X, back or "Cancelar": closes the sheet and drops the draft. */
    data object DismissCardioEdit : PlanProposalEvent

    /** A weekly cut rate (percent of body weight, 0.5..1.0) picked on a cut plan; recomputed, never requested. */
    data class CutRatePicked(val pct: Double) : PlanProposalEvent

    /** The "Con semana de descanso" switch changed on a cut plan. */
    data class DietBreakToggled(val on: Boolean) : PlanProposalEvent
}

sealed interface PlanProposalEffect {
    /** The plan was accepted and the Diario targets changed: confirm haptic, message, navigate back. */
    data object Accepted : PlanProposalEffect

    /** The plan was discarded: navigate back. */
    data object Discarded : PlanProposalEffect

    /** The cardio minutes were stored and the sheet closed: confirm haptic, no message. */
    data object CardioSaved : PlanProposalEffect

    data class ActionFailed(val action: PlanAction, val offline: Boolean) : PlanProposalEffect
}
