package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.LoadExerciseCatalog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Routine details: the routine's exercises with their planned sets, when it was last done, and the actions start,
 * delete (at once, no dialog) and, on the screen, edit. Nothing is stored here: it is rebuilt from [routineId], and
 * [RoutineDetailsEvent.Refresh] reloads it when the user comes back from the builder. Start and delete need the
 * network.
 */
class RoutineDetailsViewModel(
    private val routineId: String,
    private val routines: RoutineRepository,
    private val sessions: WorkoutSessionRepository,
    private val loadCatalog: LoadExerciseCatalog,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutineDetailsUiState>(RoutineDetailsUiState.Loading)
    val uiState: StateFlow<RoutineDetailsUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<RoutineDetailsEffect>(Channel.BUFFERED)
    val effects: Flow<RoutineDetailsEffect> = effectChannel.receiveAsFlow()

    private var online = true

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                updateContent { it.copy(isOnline = isOnline) }
            }
        }
        load(showLoading = true)
    }

    fun onEvent(event: RoutineDetailsEvent) {
        when (event) {
            RoutineDetailsEvent.Retry -> if (_uiState.value == RoutineDetailsUiState.Error) load(showLoading = true)
            RoutineDetailsEvent.Refresh -> if (_uiState.value is RoutineDetailsUiState.Content) load(false)
            RoutineDetailsEvent.Start -> start()
            RoutineDetailsEvent.Delete -> delete()
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) _uiState.value = RoutineDetailsUiState.Loading
        viewModelScope.launch {
            val routine = routines.get(routineId).getOrNull()
            val catalog = loadCatalog().getOrNull()?.associateBy(CatalogExercise::id)
            if (routine == null || catalog == null) {
                if (showLoading) _uiState.value = RoutineDetailsUiState.Error
                return@launch
            }
            val lastDone = sessions.lastCompletedAt(routineId).getOrNull()
            val exercises = routine.exercises.sortedBy { it.position }.map { exercise ->
                val entry = catalog[exercise.exerciseId]
                RoutineDetailsExerciseUi(
                    exerciseId = exercise.exerciseId,
                    name = entry?.name,
                    imageKey = entry?.takeIf { it.hasMedia }?.exercise?.key,
                    plannedSets = exercise.plannedSets,
                )
            }
            _uiState.update { current ->
                val busy = (current as? RoutineDetailsUiState.Content)?.busy ?: false
                RoutineDetailsUiState.Content(routine.name, exercises, lastDone, isOnline = online, busy = busy)
            }
        }
    }

    private fun start() {
        if (!canAct()) return
        updateContent { it.copy(busy = true) }
        viewModelScope.launch {
            effectChannel.trySend(
                when (sessions.startFromRoutine(routineId).toStartOutcome()) {
                    SessionStartOutcome.Started -> RoutineDetailsEffect.OpenLiveSession
                    SessionStartOutcome.AlreadyInProgress -> RoutineDetailsEffect.SessionInProgress
                    SessionStartOutcome.Failed -> RoutineDetailsEffect.StartFailed
                },
            )
            updateContent { it.copy(busy = false) }
        }
    }

    private fun delete() {
        if (!canAct()) return
        updateContent { it.copy(busy = true) }
        viewModelScope.launch {
            routines.delete(routineId).fold(
                onSuccess = { effectChannel.trySend(RoutineDetailsEffect.Deleted) },
                onFailure = { _ ->
                    effectChannel.trySend(RoutineDetailsEffect.ActionFailed)
                    updateContent { it.copy(busy = false) }
                },
            )
        }
    }

    /** Start and delete run only on loaded content, one at a time, online; offline they report the refusal. */
    private fun canAct(): Boolean {
        val idle = (_uiState.value as? RoutineDetailsUiState.Content)?.busy == false
        if (idle && !online) effectChannel.trySend(RoutineDetailsEffect.OfflineRefused)
        return idle && online
    }

    private fun updateContent(transform: (RoutineDetailsUiState.Content) -> RoutineDetailsUiState.Content) {
        _uiState.update { if (it is RoutineDetailsUiState.Content) transform(it) else it }
    }
}
