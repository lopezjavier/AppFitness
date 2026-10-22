package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.usecase.SessionSummary
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import kotlin.time.Duration
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
    /** The stored type of the set (planned in the routine); shown by the index badge, not editable here. */
    val type: SetType = SetType.Working,
    /** Last time's set as "85 kg × 8"; null when there is none (the UI shows a dash). */
    val previous: String? = null,
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
    /** Rest after each set of this exercise in this session (0 = no timer); editable per session only. */
    val restSeconds: Int = RoutineBounds.DEFAULT_REST_SECONDS,
)

/** The running rest countdown: [remaining] of [total] (the total grows when time is added past it). */
data class RestTimerUi(val remaining: Duration, val total: Duration)

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
        /** Null when no rest is running. */
        val restTimer: RestTimerUi? = null,
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

    /** Tap on the "ANTERIOR" cell: fills the weight and reps drafts with last time's values. */
    data class UsePrevious(val position: Int, val setIndex: Int) : LiveSessionEvent

    /** "+30s" on the rest bar. */
    data object RestAdd30 : LiveSessionEvent

    /** "Omitir" on the rest bar. */
    data object RestSkip : LiveSessionEvent

    /** Rest of the exercise for the following sets of this session only (the routine is not changed). */
    data class SetExerciseRest(val position: Int, val seconds: Int) : LiveSessionEvent

    /** The screen is visible again: the countdown is recomputed from the clock at once. */
    data object Resume : LiveSessionEvent

    /** The screen went to the background: the rest end will not buzz or beep. */
    data object Pause : LiveSessionEvent

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
