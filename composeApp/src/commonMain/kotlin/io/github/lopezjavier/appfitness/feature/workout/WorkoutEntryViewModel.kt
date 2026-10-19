package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * Decides whether the Entreno tab opens the live session or "Mis rutinas". When the lookup fails the user still gets
 * "Mis rutinas" (starting needs a connection anyway) and the live session comes back on the next [recheck].
 */
class WorkoutEntryViewModel(private val repository: WorkoutSessionRepository) : ViewModel() {
    private val _state = MutableStateFlow<WorkoutEntryState>(WorkoutEntryState.Checking)
    val state: StateFlow<WorkoutEntryState> = _state.asStateFlow()

    init {
        lookUp()
    }

    /** Looks again, for example after a session was started, finished or discarded. */
    fun recheck() = lookUp()

    private fun lookUp() {
        viewModelScope.launch {
            val inProgress = repository.getInProgress().getOrNull()
            _state.value = if (inProgress != null) WorkoutEntryState.Live else WorkoutEntryState.Routines
        }
    }
}
