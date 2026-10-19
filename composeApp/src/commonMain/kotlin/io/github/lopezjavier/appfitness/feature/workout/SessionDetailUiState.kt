package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SessionStats

/** One completed set of a past session. */
data class DetailSet(val setIndex: Int, val setType: SetType, val weightKg: Double, val reps: Int)

/** An exercise of the session; [name] is null when the library has none (the UI shows a generic label). */
data class DetailExercise(
    val position: Int,
    val exerciseId: String,
    val name: String?,
    val sets: List<DetailSet>,
    val isPersonalRecord: Boolean,
)

/** The set being corrected: [weight] and [reps] are the typed drafts; [invalid] marks a refused save. */
data class EditSetState(
    val position: Int,
    val setIndex: Int,
    val weight: String,
    val reps: String,
    val invalid: Boolean = false,
)

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState

    /** The session could not be loaded (or no longer exists); [SessionDetailEvent.Retry] tries again. */
    data object Error : SessionDetailUiState

    /**
     * A completed session. Editing and deleting need the server, so both are ignored while [isOnline] is false (the
     * UI shows the reason). [busy] is true while a save or a deletion runs.
     */
    data class Content(
        val name: String,
        val label: DateLabel,
        val stats: SessionStats,
        val exercises: List<DetailExercise>,
        val isOnline: Boolean = true,
        val edit: EditSetState? = null,
        val deleteConfirm: Boolean = false,
        val busy: Boolean = false,
    ) : SessionDetailUiState
}

sealed interface SessionDetailEvent {
    data object Retry : SessionDetailEvent

    /** Tap on a set: opens the correction sheet prefilled with its values. */
    data class EditSet(val position: Int, val setIndex: Int) : SessionDetailEvent

    data class WeightChanged(val text: String) : SessionDetailEvent

    data class RepsChanged(val text: String) : SessionDetailEvent

    data object SaveSet : SessionDetailEvent

    data object DismissEdit : SessionDetailEvent

    data object RequestDelete : SessionDetailEvent

    data object ConfirmDelete : SessionDetailEvent

    data object DismissDelete : SessionDetailEvent
}

sealed interface SessionDetailEffect {
    /** The session was deleted: leave the detail. */
    data object Deleted : SessionDetailEffect

    /** A save or a deletion failed: show a message. */
    data object ActionFailed : SessionDetailEffect
}
