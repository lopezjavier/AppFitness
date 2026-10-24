package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val KEY_STEP = "past_chooser_step"

/** The screens of the source chooser sheet: the three options, or one of the two pickers. */
enum class PastWorkoutChooserStep { Closed, Options, Routines, Sessions }

/** The routines offered by the "Desde una rutina" step. */
sealed interface ChooserRoutines {
    data object Loading : ChooserRoutines

    data object Empty : ChooserRoutines

    data object Error : ChooserRoutines

    data class Content(val routines: List<RoutineSummary>) : ChooserRoutines
}

data class PastWorkoutChooserUiState(
    val step: PastWorkoutChooserStep = PastWorkoutChooserStep.Closed,
    val routines: ChooserRoutines = ChooserRoutines.Loading,
)

sealed interface PastWorkoutChooserEvent {
    /** The "+" of the history: opens the sheet on its options. */
    data object Open : PastWorkoutChooserEvent

    /** Closes the sheet (X, scrim, swipe, back) without choosing. */
    data object Dismiss : PastWorkoutChooserEvent

    /** The back arrow of a picker: returns to the options. */
    data object Back : PastWorkoutChooserEvent

    data object ChooseEmpty : PastWorkoutChooserEvent

    data object ChooseRoutine : PastWorkoutChooserEvent

    data object ChooseWorkout : PastWorkoutChooserEvent

    data class PickRoutine(val routineId: String) : PastWorkoutChooserEvent

    /** A row of the history list (rendered by the history with its own row). */
    data class PickWorkout(val sessionId: String) : PastWorkoutChooserEvent

    data object RetryRoutines : PastWorkoutChooserEvent
}

sealed interface PastWorkoutChooserEffect {
    /** A source was chosen: open the past-workout form for it. The sheet is already closed. */
    data class Start(val source: PastWorkoutSource) : PastWorkoutChooserEffect
}

/**
 * State of the source chooser opened from the history (US-37): which step is showing (kept in [savedState], ADR-40) and
 * the routines of the routine picker. The workout picker reuses the history's own rows, so it has no list here.
 */
class PastWorkoutChooserViewModel(
    private val routineRepository: RoutineRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var step: PastWorkoutChooserStep = savedState.get<String>(KEY_STEP)
        ?.let { name -> PastWorkoutChooserStep.entries.firstOrNull { it.name == name } }
        ?: PastWorkoutChooserStep.Closed
    private var routines: ChooserRoutines = ChooserRoutines.Loading

    private val _uiState = MutableStateFlow(PastWorkoutChooserUiState(step, routines))
    val uiState: StateFlow<PastWorkoutChooserUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<PastWorkoutChooserEffect>(Channel.BUFFERED)
    val effects: Flow<PastWorkoutChooserEffect> = effectChannel.receiveAsFlow()

    init {
        if (step == PastWorkoutChooserStep.Routines) loadRoutines()
    }

    fun onEvent(event: PastWorkoutChooserEvent) {
        when (event) {
            PastWorkoutChooserEvent.Open -> moveTo(PastWorkoutChooserStep.Options)

            PastWorkoutChooserEvent.Dismiss -> moveTo(PastWorkoutChooserStep.Closed)

            PastWorkoutChooserEvent.Back -> moveTo(PastWorkoutChooserStep.Options)

            PastWorkoutChooserEvent.ChooseEmpty -> choose(PastWorkoutSource.Empty)

            PastWorkoutChooserEvent.ChooseRoutine -> {
                moveTo(PastWorkoutChooserStep.Routines)
                loadRoutines()
            }

            PastWorkoutChooserEvent.ChooseWorkout -> moveTo(PastWorkoutChooserStep.Sessions)

            is PastWorkoutChooserEvent.PickRoutine -> choose(PastWorkoutSource.FromRoutine(event.routineId))

            is PastWorkoutChooserEvent.PickWorkout -> choose(PastWorkoutSource.FromSession(event.sessionId))

            PastWorkoutChooserEvent.RetryRoutines -> if (routines == ChooserRoutines.Error) loadRoutines()
        }
    }

    private fun choose(source: PastWorkoutSource) {
        moveTo(PastWorkoutChooserStep.Closed)
        effectChannel.trySend(PastWorkoutChooserEffect.Start(source))
    }

    private fun moveTo(next: PastWorkoutChooserStep) {
        step = next
        savedState[KEY_STEP] = next.name
        publish()
    }

    private fun loadRoutines() {
        routines = ChooserRoutines.Loading
        publish()
        viewModelScope.launch {
            routines = routineRepository.listActive().fold(
                onSuccess = { if (it.isEmpty()) ChooserRoutines.Empty else ChooserRoutines.Content(it) },
                onFailure = { ChooserRoutines.Error },
            )
            publish()
        }
    }

    private fun publish() {
        _uiState.value = PastWorkoutChooserUiState(step, routines)
    }
}
