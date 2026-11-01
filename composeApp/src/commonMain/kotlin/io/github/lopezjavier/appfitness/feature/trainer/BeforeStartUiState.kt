package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputs

/** What "Antes de empezar" is about to create; the route argument is [argument]. */
enum class BeforeStartMode(val argument: String, val kind: PlanInputKind) {
    Plan("plan", PlanInputKind.Plan),
    Block("block", PlanInputKind.Block),
    ;

    companion object {
        /** Unknown or missing arguments fall back to the plan. */
        fun fromArgument(argument: String?): BeforeStartMode = entries.firstOrNull { it.argument == argument } ?: Plan
    }
}

sealed interface BeforeStartUiState {
    data object Loading : BeforeStartUiState

    /**
     * The inputs of the creation. [sheet] is the activity level of the open sheet (null when closed); [creating] is
     * true while the confirm call of "Crear" is in flight; [savingLevel] while a level picked in the sheet is saved.
     */
    data class Content(
        val inputs: PlanInputs,
        val mode: BeforeStartMode,
        val planId: String?,
        val replacingPlanId: String?,
        val creating: Boolean = false,
        val sheet: ActivityLevel? = null,
        val savingLevel: Boolean = false,
    ) : BeforeStartUiState {
        val rows: List<PlanInputRow> = inputs.rows()

        /** Blocking missing inputs, shown first as "Falta: ..." rows. */
        val flags: List<PlanInputRow> get() = rows.filter { it.isFlag }

        /** Non-blocking missing inputs: "Sin dato: ..." notes. */
        val missingNotes: List<PlanInputRow> get() = rows.filter { it.marker == PlanInputMarker.NoData }

        /** The old weigh-in caution; null when the weight is recent or absent. */
        val staleWeight: PlanInputRow?
            get() = rows.firstOrNull { it.key == PlanInputKey.Weight && it.marker == PlanInputMarker.Stale }

        val hasProvisional: Boolean get() = rows.any { it.isProvisional }

        /** Disabled-footer reason applies (the UI also disables the footer while offline). */
        val hasBlocking: Boolean get() = inputs.hasBlocking

        val canCreate: Boolean get() = !hasBlocking && !creating && !savingLevel

        /** The plan the new one would replace; only a plan creation replaces something. */
        val replaces: CurrentPlan? get() = if (mode == BeforeStartMode.Plan) inputs.currentPlan else null
    }

    data object Offline : BeforeStartUiState

    data object Error : BeforeStartUiState
}

sealed interface BeforeStartEvent {
    data object Load : BeforeStartEvent

    data object Retry : BeforeStartEvent

    /** Back from an editor: silent reload that keeps the shown content. */
    data object Refresh : BeforeStartEvent

    data object OpenSheet : BeforeStartEvent

    /** A card tapped in the sheet: confirms every value with that level at once (R-12). */
    data class PickLevel(val level: ActivityLevel) : BeforeStartEvent

    data object CloseSheet : BeforeStartEvent

    /** The pinned footer: confirms the shown provisional values (if any) and moves on. */
    data object Create : BeforeStartEvent
}

sealed interface BeforeStartEffect {
    /** Values confirmed (or nothing to confirm): navigate to the proposal or the block preview. */
    data class Created(val mode: BeforeStartMode, val planId: String?, val replacingPlanId: String?) :
        BeforeStartEffect

    /** Confirming failed (from the footer or the sheet); nothing changed. [offline] picks the offline hint. */
    data class CreateFailed(val offline: Boolean) : BeforeStartEffect
}
