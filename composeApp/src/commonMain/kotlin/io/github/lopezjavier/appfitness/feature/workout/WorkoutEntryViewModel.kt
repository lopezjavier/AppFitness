package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the Entreno tab shows. */
sealed interface WorkoutEntryState {
    /** Looking for an in-progress session. */
    data object Checking : WorkoutEntryState

    /** A session is in progress (also after a cold start): show the live screen. */
    data object Live : WorkoutEntryState

    /** No session in progress: show "Mis rutinas". */
    data object Routines : WorkoutEntryState
}

/** Discarding the in-progress session from the "Tienes un entreno en curso" card. */
sealed interface WorkoutEntryEvent {
    /** The trash button was tapped: ask for confirmation. */
    data object RequestDiscard : WorkoutEntryEvent

    data object DismissDiscard : WorkoutEntryEvent

    data object ConfirmDiscard : WorkoutEntryEvent
}

/** One-off effects of the Entreno tab. */
sealed interface WorkoutEntryEffect {
    /** Discarding failed; the session is still in progress. */
    data object DiscardFailed : WorkoutEntryEffect
}

/**
 * Decides whether the Entreno tab opens the live session or "Mis rutinas". When the lookup fails the user still gets
 * "Mis rutinas" (starting needs a connection anyway) and the live session comes back on the next [recheck].
 * Also runs the discard from the in-progress card; [discardAsked] survives recreation (ADR-40).
 */
class WorkoutEntryViewModel(
    private val repository: WorkoutSessionRepository,
    connectivity: ConnectivityObserver,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow<WorkoutEntryState>(WorkoutEntryState.Checking)
    val state: StateFlow<WorkoutEntryState> = _state.asStateFlow()

    private val _discardAsked = MutableStateFlow(savedState.get<Boolean>(KEY_DISCARD_ASKED) == true)

    /** True while the discard confirmation is open. */
    val discardAsked: StateFlow<Boolean> = _discardAsked.asStateFlow()

    private val _discarding = MutableStateFlow(false)

    /** True while the discard request runs (the card's buttons are disabled). */
    val discarding: StateFlow<Boolean> = _discarding.asStateFlow()

    private val _isOnline = MutableStateFlow(true)

    /** Discarding needs the server; the trash button is disabled while this is false. */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val effectChannel = Channel<WorkoutEntryEffect>(Channel.BUFFERED)
    val effects: Flow<WorkoutEntryEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { connectivity.isOnline.collect { _isOnline.value = it } }
        lookUp()
    }

    /** Looks again, for example after a session was started, finished or discarded. */
    fun recheck() = lookUp()

    fun onEvent(event: WorkoutEntryEvent) {
        when (event) {
            WorkoutEntryEvent.RequestDiscard -> if (_isOnline.value && !_discarding.value) setDiscardAsked(true)
            WorkoutEntryEvent.DismissDiscard -> setDiscardAsked(false)
            WorkoutEntryEvent.ConfirmDiscard -> confirmDiscard()
        }
    }

    private fun setDiscardAsked(value: Boolean) {
        _discardAsked.value = value
        savedState[KEY_DISCARD_ASKED] = value
    }

    private fun confirmDiscard() {
        if (!_discardAsked.value || _discarding.value) return
        setDiscardAsked(false)
        if (!_isOnline.value) return
        _discarding.value = true
        viewModelScope.launch {
            val id = repository.getInProgress().getOrNull()?.id
            val discarded = id != null && repository.discard(id).isSuccess
            _discarding.value = false
            if (discarded) {
                _state.value = WorkoutEntryState.Routines
            } else {
                effectChannel.trySend(WorkoutEntryEffect.DiscardFailed)
            }
        }
    }

    private fun lookUp() {
        viewModelScope.launch {
            val inProgress = repository.getInProgress().getOrNull()
            _state.value = if (inProgress != null) WorkoutEntryState.Live else WorkoutEntryState.Routines
        }
    }

    private companion object {
        const val KEY_DISCARD_ASKED = "workout_discard_asked"
    }
}
