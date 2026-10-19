package io.github.lopezjavier.appfitness.feature.analytics

import io.github.lopezjavier.appfitness.domain.model.ExerciseProgress
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary

/** A top exercise chip; [name] is null when the library has none (the UI shows a generic label). */
data class StrengthChip(val exerciseId: String, val name: String?)

sealed interface StrengthUiState {
    data object Loading : StrengthUiState

    /** No completed session in the last 6 weeks: the UI invites to start a workout. */
    data object Empty : StrengthUiState

    /** The data could not be read (also when offline); [StrengthEvent.Retry] tries again. */
    data object Error : StrengthUiState

    /**
     * Top exercises with [selectedId] chosen. [summary] is null while the record of a newly selected exercise loads.
     * The UI shows the monthly delta by its sign, and hides it when [StrengthSummary.monthlyDelta] is null.
     */
    data class Content(val chips: List<StrengthChip>, val selectedId: String, val summary: StrengthSummary?) :
        StrengthUiState
}

sealed interface StrengthEvent {
    data object Retry : StrengthEvent

    data class SelectExercise(val exerciseId: String) : StrengthEvent
}

sealed interface ExerciseProgressUiState {
    data object Loading : ExerciseProgressUiState

    /** The exercise has no completed session yet. */
    data object Empty : ExerciseProgressUiState

    data object Error : ExerciseProgressUiState

    /**
     * Charts of the exercise in [range]. [ExerciseProgress.hasEnoughData] false means a single session: the UI shows
     * its value and the "at least 2 sessions" message. [name] is null when the library has none.
     */
    data class Content(val name: String?, val range: ProgressRange, val progress: ExerciseProgress) :
        ExerciseProgressUiState
}

sealed interface ExerciseProgressEvent {
    data object Retry : ExerciseProgressEvent

    data class SelectRange(val range: ProgressRange) : ExerciseProgressEvent
}
