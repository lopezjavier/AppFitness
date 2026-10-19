package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import kotlin.time.Instant

/** One set row of the live screen. [weight] and [reps] are the editable drafts (text as typed). */
data class LiveSetRow(
    val setIndex: Int,
    val weight: String,
    val reps: String,
    val completed: Boolean,
    /** Previous set's values, shown greyed on an added set; null when there is nothing to suggest. */
    val weightPlaceholder: String? = null,
    val repsPlaceholder: String? = null,
    /** The last write of this set has not reached the server yet; it is retried when the connection returns. */
    val pendingSave: Boolean = false,
    /** Checking was refused because the weight or the reps are missing; cleared by the next edit. */
    val invalid: Boolean = false,
)

/** An exercise card. [name] is null when the library has no name for it (the UI shows a generic label). */
data class LiveExercise(
    val position: Int,
    val exerciseId: String,
    val name: String?,
    val supersetLabel: SupersetLabel?,
    val sets: List<LiveSetRow>,
)

/** The confirmation sheet of "Finalizar entreno": [pendingCount] sets are not completed and will be dropped. */
data class FinishSheet(val pendingCount: Int)

/** Result of a finished session, ready to render. [exercises] follow the session order. */
data class LiveSummary(val summary: SessionSummary, val exerciseNames: Map<Int, String?>)

sealed interface LiveSessionUiState {
    /** Loading the in-progress session. */
    data object Loading : LiveSessionUiState

    /** Loading failed; [LiveSessionEvent.Retry] tries again. */
    data object Error : LiveSessionUiState

    /** There is no in-progress session (finished or discarded elsewhere): the screen goes back to "Mis rutinas". */
    data object NoSession : LiveSessionUiState

    /**
     * The running session. Finishing and discarding need the server, so [actionsBlocked] (offline) disables both and
     * the UI shows the reason; [busy] is true while one of them runs.
     */
    data class Active(
        val name: String,
        val startedAt: Instant,
        val exercises: List<LiveExercise>,
        val isOnline: Boolean = true,
        val finishSheet: FinishSheet? = null,
        val discardConfirm: Boolean = false,
        val busy: Boolean = false,
    ) : LiveSessionUiState {
        val actionsBlocked: Boolean get() = !isOnline
        val queuedCount: Int get() = exercises.sumOf { exercise -> exercise.sets.count { it.pendingSave } }
    }

    /** The session is finished; the summary stays until [LiveSessionEvent.Done]. */
    data class Finished(val summary: LiveSummary) : LiveSessionUiState
}

sealed interface LiveSessionEvent {
    data object Retry : LiveSessionEvent

    data class WeightChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    data class RepsChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    /** The checkbox of a set: [checked] true completes it, false makes it editable again. */
    data class SetChecked(val position: Int, val setIndex: Int, val checked: Boolean) : LiveSessionEvent

    data class AddSet(val position: Int) : LiveSessionEvent

    data object AddExercise : LiveSessionEvent

    /** The picker returned [exerciseId] (the result of [LiveSessionEffect.OpenPicker]). */
    data class ExerciseSelected(val exerciseId: String) : LiveSessionEvent

    data object RequestFinish : LiveSessionEvent

    data object DismissFinish : LiveSessionEvent

    data object ConfirmFinish : LiveSessionEvent

    data object RequestDiscard : LiveSessionEvent

    data object DismissDiscard : LiveSessionEvent

    data object ConfirmDiscard : LiveSessionEvent

    /** "Listo" on the summary. */
    data object Done : LiveSessionEvent
}

sealed interface LiveSessionEffect {
    /** Open the exercise picker in select mode. */
    data object OpenPicker : LiveSessionEffect

    /** Leave the live flow (back to "Mis rutinas"): discarded, summary closed or no session. */
    data object Closed : LiveSessionEffect

    /** Finishing or discarding failed; message only, the state is unchanged. */
    data object ActionFailed : LiveSessionEffect
}
