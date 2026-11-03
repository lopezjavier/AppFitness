package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CurrentPlan
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.RoutineFolder

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

/** Which sheet of the block's start choice is open (US-93): none, "Desde cero / Desde una carpeta", or the folders. */
enum class StartChoiceStep { None, Choice, Folders }

/** How a block starts. */
enum class StartOption { Scratch, Folder }

/** The folders offered by the folder picker; an empty [Loaded] list is the empty state. */
sealed interface FolderPickerState {
    data object Loading : FolderPickerState

    data class Loaded(val folders: List<RoutineFolder>) : FolderPickerState

    data class Failed(val offline: Boolean) : FolderPickerState
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
        /** What the user said in "Para conocerte" (empty when nothing was saved). */
        val intake: IntakeSummary = IntakeSummary(),
        /** The open sheet of the block's start choice (block mode only); survives recreation. */
        val startChoice: StartChoiceStep = StartChoiceStep.None,
        val folderPicker: FolderPickerState = FolderPickerState.Loading,
    ) : BeforeStartUiState {
        val rows: List<PlanInputRow> = inputs.rows()

        /**
         * The intake rows: three in a plan, "Tiempo entrenando" and "Para conocerte" in a block (it does not read the
         * maintenance). Never flags or blockers: an empty value reads "Sin indicar". Each one opens only its editor.
         */
        val intakeRows: List<IntakeRowKind>
            get() = if (mode == BeforeStartMode.Plan) {
                IntakeRowKind.entries
            } else {
                listOf(IntakeRowKind.TrainingYears, IntakeRowKind.FreeText)
            }

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

    /** A row of the start choice sheet: "Desde cero" goes to the splits, "Desde una carpeta" lists the folders. */
    data class PickStart(val option: StartOption) : BeforeStartEvent

    /** A folder of the picker: opens its review for the block. */
    data class PickFolder(val folderId: String) : BeforeStartEvent

    /** Closes the start choice or the folder picker without choosing. */
    data object CloseChoice : BeforeStartEvent

    /** "Reintentar" of the folder picker. */
    data object RetryFolders : BeforeStartEvent
}

sealed interface BeforeStartEffect {
    /** Values confirmed (or nothing to confirm): navigate to the proposal or the block preview. */
    data class Created(val mode: BeforeStartMode, val planId: String?, val replacingPlanId: String?) :
        BeforeStartEffect

    /** Confirming failed (from the footer or the sheet); nothing changed. [offline] picks the offline hint. */
    data class CreateFailed(val offline: Boolean) : BeforeStartEffect

    /** A folder was picked to start the block: open its review (block purpose). */
    data class OpenFolderReview(val planId: String?, val folderId: String) : BeforeStartEffect
}
