package io.github.lopezjavier.appfitness.feature.workout

import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
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
    /** The logged RPE as "7,5"; null when none (the UI shows the empty marker). */
    val rpe: String? = null,
    /** Kilometres as typed (distance and duration sets). */
    val distance: String = "",
    val steps: String = "",
    val floors: String = "",
    /** The set stopwatch's time, recomputed from the clock on every publish. */
    val durationSeconds: Int = 0,
    val stopwatchRunning: Boolean = false,
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
    /** The routine's target RPE as "8"; null when the exercise has none or the session is free. */
    val targetRpeText: String? = null,
    /** What each set of this exercise measures; decides the fields of its rows. */
    val metricType: ExerciseMetricType = ExerciseMetricType.WeightReps,
    /** Decides the hint of the weight field; null when unknown. */
    val equipment: Equipment? = null,
)

/**
 * The rest countdown: [remaining] of [total] (the total grows when time is added past it); [paused] while the session
 * is paused, when [remaining] stays frozen.
 */
data class RestTimerUi(val remaining: Duration, val total: Duration, val paused: Boolean = false)

/** The set (exercise [position], [setIndex]) whose type picker is open. */
data class TypePickerTarget(val position: Int, val setIndex: Int)

/** The set (exercise [position], [setIndex]) whose RPE picker is open. */
data class RpePickerTarget(val position: Int, val setIndex: Int, val completesSet: Boolean = false)

/** The unsaved switch of the "Mancuerna doble" sheet of [exerciseId]; nothing is sent until "Guardar". */
data class DoubleDumbbellDraft(val exerciseId: String, val enabled: Boolean)

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
        /** The set whose type picker is open; null when closed. Survives process death (ADR-40). */
        val typePicker: TypePickerTarget? = null,
        /** The set whose RPE picker is open; null when closed. Survives process death (ADR-40). */
        val rpePicker: RpePickerTarget? = null,
        /** Exercises counted double ("Contar mancuerna doble"); empty when none or unreadable. */
        val doubleDumbbellIds: Set<String> = emptySet(),
        /** The latest body weight in kg (prefills the body-weight sheet); null when there is none. */
        val bodyWeightKg: Double? = null,
        /** Today already has a Health Connect weight (ADR-47): the body-weight update is disabled. */
        val todayHasHealthConnectWeight: Boolean = false,
        /** Unsaved switch of the double-dumbbell sheet; null when not drafting. Survives process death (ADR-40). */
        val doubleDumbbellDraft: DoubleDumbbellDraft? = null,
        /** Weight as typed in the body-weight sheet; null when not drafting. Survives process death (ADR-40). */
        val bodyWeightDraft: String? = null,
        /** "Guardar" was refused because the typed weight is not valid; cleared by the next edit. */
        val bodyWeightInvalid: Boolean = false,
        /** Set while the session is paused: the timer is frozen at this instant (US-38). */
        val pausedAt: Instant? = null,
        /** Seconds of earlier, already closed pauses; the duration excludes them. */
        val pausedTotalSeconds: Long = 0,
        /** The open warm-up or plate calculator; null when closed. Survives process death (ADR-40). */
        val calculator: CalculatorUiState? = null,
    ) : LiveSessionUiState {
        val isPaused: Boolean get() = pausedAt != null

        /** "Finalizar entreno" needs at least one completed set (of any type). */
        val canFinish: Boolean get() = exercises.any { exercise -> exercise.sets.any { it.completed } }

        /** At least one exercise and every set of every exercise completed: the screen offers "Finalizar entreno". */
        val allExercisesComplete: Boolean
            get() = exercises.isNotEmpty() && exercises.all { e -> e.sets.isNotEmpty() && e.sets.all { it.completed } }
        val actionsBlocked: Boolean get() = !isOnline
        val queuedCount: Int get() = exercises.sumOf { exercise -> exercise.sets.count { it.pendingSave } }
    }

    /** The session is finished; the summary stays until [LiveSessionEvent.Done]. */
    data class Finished(val summary: LiveSummary) : LiveSessionUiState
}

sealed interface LiveSessionEvent {
    data object Retry : LiveSessionEvent

    /** Tap on the type badge of a set: opens the type picker for it. */
    data class OpenTypePicker(val position: Int, val setIndex: Int) : LiveSessionEvent

    /** The picker was closed without choosing (X, back, scrim, swipe): nothing changes. */
    data object DismissTypePicker : LiveSessionEvent

    /** "Listo" in the picker: sets the type of the set (pending or completed) and saves it; Working removes a type. */
    data class SetTypeChosen(val position: Int, val setIndex: Int, val type: SetType) : LiveSessionEvent

    /** Tap on the RPE cell of a set (pending or completed): opens the RPE picker for it. */
    data class OpenRpePicker(val position: Int, val setIndex: Int) : LiveSessionEvent

    /** The RPE picker was closed without saving: nothing changes. */
    data object DismissRpePicker : LiveSessionEvent

    /** "Guardar" in the RPE picker: sets the RPE of the set and saves it; null ("Quitar RPE") clears it. */
    data class SetRpeChosen(val position: Int, val setIndex: Int, val rpe: Double) : LiveSessionEvent

    data class WeightChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    data class RepsChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    /** Kilometres typed in a distance field. */
    data class DistanceChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    data class StepsChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    data class FloorsChanged(val position: Int, val setIndex: Int, val text: String) : LiveSessionEvent

    /** Start or stop of the set's stopwatch; stopping only fills the draft, nothing is saved until the check. */
    data class StopwatchToggled(val position: Int, val setIndex: Int) : LiveSessionEvent

    /** A time typed as mm:ss in the stopwatch, already parsed to [seconds]. */
    data class DurationEdited(val position: Int, val setIndex: Int, val seconds: Int) : LiveSessionEvent

    /** The checkbox of a set: [checked] true completes it, false makes it editable again. */
    data class SetChecked(val position: Int, val setIndex: Int, val checked: Boolean) : LiveSessionEvent

    data class AddSet(val position: Int) : LiveSessionEvent

    /**
     * Swipe (or the accessibility action) on a set: removes it at once, renumbers the later sets of the exercise and
     * deletes it on the server. There is no undo (ADR-45).
     */
    data class DeleteSet(val position: Int, val setIndex: Int) : LiveSessionEvent

    /** "Pausar entreno": freezes the session duration; blocked offline. */
    data object PauseSession : LiveSessionEvent

    /** "Reanudar entreno": the paused span stops counting towards the duration; blocked offline. */
    data object ResumeSession : LiveSessionEvent

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

    /** The switch of the "Mancuerna doble" sheet changed (draft only). */
    data class DoubleDumbbellDraftChanged(val exerciseId: String, val enabled: Boolean) : LiveSessionEvent

    /** "Guardar" in the "Mancuerna doble" sheet: stores the option (all sessions, past and future). */
    data class SetDoubleDumbbell(val exerciseId: String, val enabled: Boolean) : LiveSessionEvent

    /** The weight field of the body-weight sheet changed (draft only). */
    data class BodyWeightDraftChanged(val text: String) : LiveSessionEvent

    /** "Guardar" in the body-weight sheet: stores [text] (kg, decimal comma) as today's manual weight. */
    data class SaveBodyWeight(val text: String) : LiveSessionEvent

    /** Either options sheet was closed without saving (X, back, scrim, swipe): both drafts are dropped. */
    data object DismissVolumeDrafts : LiveSessionEvent

    /** The exercise menu's warm-up or plate calculator: opens the sheet prefilled from the exercise. */
    data class OpenCalculator(val position: Int, val kind: CalculatorKind) : LiveSessionEvent

    /** The weight field of the open calculator changed (draft only). */
    data class CalculatorWeightChanged(val text: String) : LiveSessionEvent

    /** The bar selector of the open calculator changed (per use, not saved). */
    data class CalculatorBarChanged(val barKg: Double) : LiveSessionEvent

    /** The warm-up set count (1..4) of the open calculator changed (per use, not saved; ADR-55). */
    data class CalculatorWarmUpSetsChanged(val sets: Int) : LiveSessionEvent

    /**
     * "Añadir series": inserts [sets] (the rows the warm-up sheet shows) as `warm_up` sets before the first
     * non-warm-up set of the exercise, renumbers the rest and closes the calculator.
     */
    data class AddWarmUpSets(val position: Int, val sets: List<WarmUpSet>) : LiveSessionEvent

    /** The calculator was closed (X, back, scrim, swipe, "Cerrar"): nothing is inserted. */
    data object DismissCalculator : LiveSessionEvent

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

    /** Saving the double-dumbbell option or the body weight failed; the sheet stays open with its draft. */
    data object OptionSaveFailed : LiveSessionEffect
}
