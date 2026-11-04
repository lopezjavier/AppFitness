package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.coach.builder.DaysCheck
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockDraft
import io.github.lopezjavier.appfitness.domain.coach.builder.FolderBlockStatus
import io.github.lopezjavier.appfitness.domain.coach.builder.PickerEntry
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.usecase.GroupRow

/** What the folder-block picker chain is showing: the group chooser, the routine chooser or an exercise picker. */
sealed interface FolderPickerTarget {
    /** "Grupo muscular": the unmet groups (only shown when more than one is unmet). */
    data object ChooseGroup : FolderPickerTarget

    /** "Anadir a la rutina": the lifting routines (only shown when the folder has more than one). */
    data class ChooseRoutine(val group: MuscleGroup) : FolderPickerTarget

    /** The exercise picker to add a [group] exercise to routine [routineIndex]. */
    data class Add(val group: MuscleGroup, val routineIndex: Int) : FolderPickerTarget

    /** The exercise picker to swap the row [exerciseIndex] of routine [routineIndex]. */
    data class Swap(val routineIndex: Int, val exerciseIndex: Int) : FolderPickerTarget
}

/** "Desde una carpeta" (US-93): a deterministic review of a folder copy; no model call, the only write is `Save`. */
sealed interface FolderBlockUiState {
    data object Loading : FolderBlockUiState

    /** The folder or the preferences could not be read; [offline] tells a network failure from any other. */
    data class Failed(val offline: Boolean) : FolderBlockUiState

    /** The folder has no routines. */
    data class Empty(val folderName: String) : FolderBlockUiState

    /**
     * The days rule refuses the folder; nothing can be edited. [days] is never [DaysCheck.Ok]; [daysPerWeek] is the
     * user's "Días por semana".
     */
    data class Refused(val folderName: String, val days: DaysCheck, val daysPerWeek: Int) : FolderBlockUiState

    @Suppress("LongParameterList") // Everything the screen shows.
    data class Content(
        val folderName: String,
        /** The name the block will be saved under ("{folder} · bloque", unique). */
        val name: String,
        val draft: FolderBlockDraft,
        val status: FolderBlockStatus,
        val pickerTarget: FolderPickerTarget?,
        val pickerEntries: List<PickerEntry>,
        val exercises: Map<String, CatalogExercise>,
        val favouriteIds: Set<String>,
        val saving: Boolean,
        val saveError: BuilderSaveError?,
        val discardDialog: Boolean,
        /** The user changed the copy (add, swap, remove or rep fix). */
        val edited: Boolean,
    ) : FolderBlockUiState {
        val canSave: Boolean get() = status.canSave && !saving && saveError != BuilderSaveError.PlanClosed

        /** The unmet groups the "Anadir ejercicio" chain offers; empty at 100 %. */
        val addableGroups: List<GroupRow> get() = draft.addableGroups()

        /** The lifting routines (index in the folder, name) the chain offers. */
        val liftingRoutines: List<Pair<Int, String>>
            get() = draft.routines.withIndex().filter { it.value.lifting }.map { it.index to it.value.name }
    }
}

sealed interface FolderBlockEvent {
    /** Starts the add chain; skips the group and routine choosers when they have a single option. */
    data object OpenAdd : FolderBlockEvent

    data class ChooseGroup(val group: MuscleGroup) : FolderBlockEvent

    data class ChooseRoutine(val routineIndex: Int) : FolderBlockEvent

    data class OpenSwap(val routineIndex: Int, val exerciseIndex: Int) : FolderBlockEvent

    data object ClosePicker : FolderBlockEvent

    data class PickExercise(val exerciseId: String) : FolderBlockEvent

    data class RemoveAdded(val routineIndex: Int, val exerciseIndex: Int) : FolderBlockEvent

    data class ApplyRepFix(val routineIndex: Int, val exerciseIndex: Int) : FolderBlockEvent

    data object Save : FolderBlockEvent

    data object Back : FolderBlockEvent

    data object ConfirmDiscard : FolderBlockEvent

    data object CancelDiscard : FolderBlockEvent

    data object Retry : FolderBlockEvent
}

sealed interface FolderBlockEffect {
    /** Saved: same navigation as the builder. */
    data object Saved : FolderBlockEffect

    /** Leave the screen (back with nothing edited, or discard confirmed). */
    data object Close : FolderBlockEffect

    /** Confirm haptic (a pick, an add, a rep fix, a save). */
    data object Confirm : FolderBlockEffect

    /** Reject haptic (a save failure). */
    data object Reject : FolderBlockEffect
}
