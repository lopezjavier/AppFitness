package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
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

    /** A saved block exists: "Crear plan nuevo" is closed, "Bloque nuevo" starts another one. */
    data object BlockInProgress : PlanFailureKind

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
        CoachFailure.InvalidBlock,
        CoachFailure.Other,
        -> PlanFailureKind.Other

        CoachFailure.BlockInProgress -> PlanFailureKind.BlockInProgress
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

    /** The activity level changed after the plan was made: the plan must be created again. */
    Stale,
}

sealed interface PlanProposalUiState {
    /** Reading the stored plan (R-31 skeleton). */
    data object Loading : PlanProposalUiState

    /** No proposed plan: the screen asks the coach once with [PlanProposalEvent.Request]. */
    data object Idle : PlanProposalUiState

    /** Waiting for the model (indeterminate progress). */
    data object Requesting : PlanProposalUiState

    /**
     * A proposed [plan]. [unconfirmedValues] are the inputs the server would refuse the plan for (`blocksAccept` and
     * provisional): the user edits or confirms them here and accepts without creating the plan again (R-59).
     * [accepting] and [discarding] are true while that call is in flight. [dataChanged] is set when the server refused
     * the plan for unconfirmed values (the list may then be empty: the screen falls back to a generic note). [stale] is
     * true when the activity level changed after the plan was made: the plan must be created again.
     */
    data class Proposal(
        val plan: CoachPlan,
        val unconfirmedValues: List<PlanInputRow> = emptyList(),
        val stale: Boolean = false,
        /** True while "Confirmar valores" is being stored. */
        val confirming: Boolean = false,
        /** The open activity-level sheet (its draft); null when closed. */
        val activitySheet: ActivityLevel? = null,
        val savingLevel: Boolean = false,
        /** What the user stated, the starting point of the years sheet. */
        val intake: IntakeSummary = IntakeSummary(),
        val accepting: Boolean = false,
        val discarding: Boolean = false,
        val dataChanged: Boolean = false,
        /** The open cardio-minutes sheet; null when closed or when the plan has no activity targets. */
        val cardioEdit: CardioEdit? = null,
        /** True while the cut rate or the diet break is being stored and the plan recomputed. */
        val optionsSaving: Boolean = false,
    ) : PlanProposalUiState {
        val busy: Boolean
            get() = accepting || discarding || optionsSaving || confirming || savingLevel || cardioEdit?.saving == true

        /** The rate selector and the diet-break switch exist only on a cut plan. */
        val hasCutOptions: Boolean get() = plan.phase == Phase.Cut

        /** True while a value blocks the acceptance: a listed one, or the server said so and the list is empty. */
        val unconfirmed: Boolean get() = unconfirmedValues.isNotEmpty() || dataChanged

        /** US-68 gate: nothing blocking, not stale and no call in flight. */
        val canAccept: Boolean get() = !unconfirmed && !stale && !busy

        val acceptBlockedReason: AcceptBlockedReason?
            get() = when {
                dataChanged -> AcceptBlockedReason.DataChanged
                unconfirmed -> AcceptBlockedReason.Unconfirmed
                stale -> AcceptBlockedReason.Stale
                else -> null
            }
    }

    data class Failed(val kind: PlanFailureKind) : PlanProposalUiState

    /** The plan was accepted or discarded; the screen is leaving. */
    data object Closed : PlanProposalUiState
}

enum class PlanAction { Accept, Discard, Cardio, Options, Confirm }

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

    /** The "Descanso de dieta" switch changed on a cut plan. */
    data class DietBreakToggled(val on: Boolean) : PlanProposalEvent

    /** The screen resumed (for example after an editor): re-reads the blocking values; never requests a plan. */
    data object Refresh : PlanProposalEvent

    /** "Confirmar valores": confirms the estimates as shown and re-reads; never requests (nor bills) a plan. */
    data object ConfirmValues : PlanProposalEvent

    /** The activity-level row was tapped: opens its sheet (R-55). */
    data object OpenActivitySheet : PlanProposalEvent

    /** "Guardar" in the sheet: confirms the values with [level]; never requests a plan. */
    data class PickActivityLevel(val level: ActivityLevel) : PlanProposalEvent

    /** X, back or "Cancelar": closes the sheet and drops the draft. */
    data object CloseActivitySheet : PlanProposalEvent
}

sealed interface PlanProposalEffect {
    /** The plan was accepted and the Diario targets changed: confirm haptic, message, navigate back. */
    data object Accepted : PlanProposalEffect

    /** The plan was discarded: navigate back. */
    data object Discarded : PlanProposalEffect

    /** The cardio minutes were stored and the sheet closed: confirm haptic, no message. */
    data object CardioSaved : PlanProposalEffect

    /** The estimates were confirmed: confirm haptic, no message. */
    data object ValuesConfirmed : PlanProposalEffect

    data class ActionFailed(val action: PlanAction, val offline: Boolean) : PlanProposalEffect
}
