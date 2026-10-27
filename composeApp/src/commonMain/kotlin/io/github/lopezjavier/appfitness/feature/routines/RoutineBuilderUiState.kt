package io.github.lopezjavier.appfitness.feature.routines

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.workout.CalculatorKind
import io.github.lopezjavier.appfitness.feature.workout.CalculatorUiState

/**
 * One planned set of a row. [setId] is stable while editing (the list key). [weight] and [reps] are the text as typed
 * ([weight] empty = no planned weight); [isValid] is false while [reps] is not 1-50 or [weight] is not a number.
 */
data class PlannedSetUi(
    val setId: Long,
    val weight: String,
    val reps: String,
    val type: SetType,
    val isValid: Boolean = true,
)

/** The set whose type picker is open: [current] is the type it has now. */
data class TypePickerUi(val rowIndex: Int, val setIndex: Int, val current: SetType)

/**
 * One exercise row of the builder. [rowId] is stable while editing (the list key; positions are not), [name] and
 * [imageKey] come from the exercise catalog (null while it loads or when unknown). [supersetLabel] is non-null for
 * members of a superset ("Superserie A1").
 */
data class BuilderRowUi(
    val rowId: Long,
    val exerciseId: String,
    val name: String?,
    val imageKey: String?,
    val plannedSets: List<PlannedSetUi>,
    val restSeconds: Int,
    val supersetLabel: SupersetLabel?,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canGroupWithNext: Boolean,
    /** Target RPE of the exercise (6 to 10 in half points); null = no target. */
    val targetRpe: Double? = null,
    /** Equipment of the exercise; null while the catalog loads (equipment-dependent menu entries stay hidden). */
    val equipment: Equipment? = null,
    /** The logged weight is per hand (`Exercise.perHand`): volume counts it twice. */
    val isPerHand: Boolean = false,
) {
    val canAddSet: Boolean get() = plannedSets.size < RoutineBounds.SETS_MAX
    val canRemoveSet: Boolean get() = plannedSets.size > RoutineBounds.SETS_MIN
}

/** State of the routine builder. */
sealed interface RoutineBuilderUiState {
    /** Edit mode while the routine is being read. */
    data object Loading : RoutineBuilderUiState

    /** Reading the routine (or its exercise names) failed; [RoutineBuilderEvent.Retry] tries again. */
    data object LoadError : RoutineBuilderUiState

    /**
     * The form. [isDirty] means there are unsaved edits (the editor template asks before discarding them).
     * [nameTaken]: another routine already has this name (trimmed, any case), found locally or reported by the
     * server on save; it disables saving. [saved] turns true once the save succeeded (the template then confirms
     * and closes). Saving needs a free name, at least one exercise, a connection and no save in flight.
     */
    data class Editing(
        val isEditMode: Boolean,
        val name: String,
        val rows: List<BuilderRowUi>,
        val isOnline: Boolean = true,
        val isSaving: Boolean = false,
        val isDirty: Boolean = false,
        val nameTaken: Boolean = false,
        val saved: Boolean = false,
        val typePicker: TypePickerUi? = null,
        /** The open warm-up calculator or disc loader sheet; its inputs are never part of the routine draft. */
        val calculator: CalculatorUiState? = null,
        /** True when inserting the calculator's warm-ups would exceed the sets cap (the action is disabled). */
        val warmUpBlockedByCap: Boolean = false,
    ) : RoutineBuilderUiState {
        val canSave: Boolean
            get() = name.isNotBlank() && !nameTaken && rows.isNotEmpty() && isOnline && !isSaving &&
                rows.all { row -> row.plannedSets.all { it.isValid } }
    }
}

sealed interface RoutineBuilderEvent {
    data class SetName(val name: String) : RoutineBuilderEvent

    /** The picker returned this exercise: append it with the default 3 x 8, 120 s. */
    data class AddExercise(val exerciseId: String) : RoutineBuilderEvent

    /** A drag finished: the row at [from] now sits at [to]. */
    data class MoveTo(val from: Int, val to: Int) : RoutineBuilderEvent

    data class MoveUp(val index: Int) : RoutineBuilderEvent

    data class MoveDown(val index: Int) : RoutineBuilderEvent

    data class GroupWithNext(val index: Int) : RoutineBuilderEvent

    data class Ungroup(val index: Int) : RoutineBuilderEvent

    data class Remove(val index: Int) : RoutineBuilderEvent

    /** Appends a copy of the last set; in a superset it applies to every member so the counts stay equal. */
    data class AddSet(val index: Int) : RoutineBuilderEvent

    /** Removes one set (never the last); in a superset it removes the same set of every member. */
    data class RemoveSet(val index: Int, val setIndex: Int) : RoutineBuilderEvent

    /** [text] is the weight as typed (empty = no planned weight). */
    data class SetWeight(val index: Int, val setIndex: Int, val text: String) : RoutineBuilderEvent

    data class SetReps(val index: Int, val setIndex: Int, val text: String) : RoutineBuilderEvent

    /** Picks the type of one set (Normal clears it) and closes the picker. */
    data class ChangeSetType(val index: Int, val setIndex: Int, val type: SetType) : RoutineBuilderEvent

    data class OpenTypePicker(val index: Int, val setIndex: Int) : RoutineBuilderEvent

    data object CloseTypePicker : RoutineBuilderEvent

    /** [delta] is -1 or +1 step of the rest (10 s); the value stays within its bounds. */
    data class StepRest(val index: Int, val delta: Int) : RoutineBuilderEvent

    /** [delta] is -1 or +1 half point of the target RPE: from none +1 gives 8, below 6 clears it. */
    data class StepTargetRpe(val index: Int, val delta: Int) : RoutineBuilderEvent

    /** Opens the warm-up calculator or disc loader of row [index], prefilled with its first working weight. */
    data class OpenCalculator(val index: Int, val kind: CalculatorKind) : RoutineBuilderEvent

    data class CalculatorWeightChanged(val text: String) : RoutineBuilderEvent

    /** The disc loader: the count of [weightKg] discs per side changed (0..10, ignored for disabled plates). */
    data class DiscCountChanged(val weightKg: Double, val count: Int) : RoutineBuilderEvent

    data class CalculatorWarmUpSetsChanged(val sets: Int) : RoutineBuilderEvent

    /** Inserts the calculated warm-ups into row [index] of the draft (saved with the routine's Guardar). */
    data class AddWarmUpSets(val index: Int, val sets: List<WarmUpSet>) : RoutineBuilderEvent

    data object DismissCalculator : RoutineBuilderEvent

    /** Closes every option sheet (calculators), discarding their drafts. */
    data object DismissOptionSheets : RoutineBuilderEvent

    data object Save : RoutineBuilderEvent

    data object Retry : RoutineBuilderEvent
}

/** One-off results of the builder. */
sealed interface RoutineBuilderEffect {
    /** Saving failed (the edits are kept); [offline] distinguishes a lost connection from any other failure. */
    data class SaveFailed(val offline: Boolean) : RoutineBuilderEffect
}
