package io.github.lopezjavier.appfitness.feature.exercises

import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup

/** The "create exercise" sheet. Nothing is pre-selected, so a wrong default can never be submitted. */
data class CreateExerciseForm(
    val name: String,
    val muscleGroup: MuscleGroup? = null,
    val equipment: Equipment? = null,
    /** What the exercise's sets measure; defaults to weight and reps, so it is always valid. */
    val metricType: ExerciseMetricType = ExerciseMetricType.WeightReps,
    /** The logged weight is per hand/side (volume counts twice); off by default. */
    val perHand: Boolean = false,
    /** Optional movement pattern (E13 coach data); null = "Sin definir". */
    val movementPattern: MovementPattern? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && muscleGroup != null && equipment != null && !isSubmitting
}

sealed interface ExercisePickerUiState {
    data object Loading : ExercisePickerUiState

    /** Loading the catalog failed; [ExercisePickerEvent.Retry] tries again. */
    data object Error : ExercisePickerUiState

    /**
     * [visible] is the filtered, sorted list; [totalCount] the unfiltered size (0 means an empty catalog, not just
     * a search without results). [createForm] non-null means the create sheet is open.
     */
    data class Content(
        val visible: List<CatalogExercise>,
        val totalCount: Int,
        val query: String = "",
        val muscleGroup: MuscleGroup? = null,
        val equipment: Equipment? = null,
        val isOnline: Boolean = true,
        val createForm: CreateExerciseForm? = null,
    ) : ExercisePickerUiState {
        val canCreate: Boolean get() = isOnline
    }
}

sealed interface ExercisePickerEvent {
    data class SetQuery(val query: String) : ExercisePickerEvent

    /** Null clears the filter ("Todos"). */
    data class SelectMuscleGroup(val group: MuscleGroup?) : ExercisePickerEvent

    data class SelectEquipment(val equipment: Equipment?) : ExercisePickerEvent

    /** The row body: selects the exercise in select mode, opens its detail otherwise. */
    data class OpenExercise(val id: String) : ExercisePickerEvent

    /** The row thumbnail: always opens the detail, also in select mode. */
    data class OpenDetail(val id: String) : ExercisePickerEvent

    data object Retry : ExercisePickerEvent

    data object OpenCreate : ExercisePickerEvent

    data object DismissCreate : ExercisePickerEvent

    data class SetCreateName(val name: String) : ExercisePickerEvent

    data class SelectCreateMuscleGroup(val group: MuscleGroup) : ExercisePickerEvent

    data class SelectCreateEquipment(val equipment: Equipment) : ExercisePickerEvent

    data class SelectCreateMetricType(val metricType: ExerciseMetricType) : ExercisePickerEvent

    data class SetCreatePerHand(val perHand: Boolean) : ExercisePickerEvent

    /** Null clears the pattern ("Sin definir"). */
    data class SetCreateMovementPattern(val movementPattern: MovementPattern?) : ExercisePickerEvent

    data object SubmitCreate : ExercisePickerEvent
}

/** One-off results of the picker. */
sealed interface ExercisePickerEffect {
    /** Select mode: the user chose (or just created) this exercise. */
    data class Selected(val exerciseId: String) : ExercisePickerEffect

    /** Browse mode: show this exercise's detail. */
    data class OpenDetail(val exerciseId: String) : ExercisePickerEffect

    /** Creating the exercise failed; the sheet stays open with its inline error. */
    data object CreateFailed : ExercisePickerEffect
}
