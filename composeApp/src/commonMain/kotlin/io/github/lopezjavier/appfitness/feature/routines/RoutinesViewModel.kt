package io.github.lopezjavier.appfitness.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.RoutineSummary
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.sortRoutinesByName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Mis rutinas" (US-10): lists the active routines and runs edit, duplicate, archive and starting a session. */
class RoutinesViewModel(
    private val repository: RoutineRepository,
    private val sessions: WorkoutSessionRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _uiState = MutableStateFlow<RoutinesUiState>(RoutinesUiState.Loading)
    val uiState: StateFlow<RoutinesUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<RoutinesEffect>(Channel.BUFFERED)
    val effects: Flow<RoutinesEffect> = effectChannel.receiveAsFlow()

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

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: RoutinesEvent) {
        when (event) {
            RoutinesEvent.Retry -> if (_uiState.value == RoutinesUiState.Error) load(showLoading = true)

            RoutinesEvent.Refresh -> if (_uiState.value is RoutinesUiState.Content) load(showLoading = false)

            RoutinesEvent.NewRoutine -> effectChannel.trySend(RoutinesEffect.OpenBuilder(null))

            is RoutinesEvent.Edit -> {
                updateContent { it.copy(menuFor = null) }
                effectChannel.trySend(RoutinesEffect.OpenBuilder(event.id))
            }

            is RoutinesEvent.OpenMenu -> updateContent { content -> content.copy(menuFor = content.find(event.id)) }

            RoutinesEvent.DismissMenu -> updateContent { it.copy(menuFor = null) }

            is RoutinesEvent.Duplicate -> duplicate(event.id)

            is RoutinesEvent.RequestArchive ->
                updateContent { content ->
                    if (online) content.copy(menuFor = null, archiveFor = content.find(event.id)) else content
                }

            RoutinesEvent.ConfirmArchive -> confirmArchive()

            RoutinesEvent.DismissArchive -> updateContent { it.copy(archiveFor = null) }

            is RoutinesEvent.StartRoutine -> start { sessions.startFromRoutine(event.id) }
        }
    }

    private fun load(showLoading: Boolean) {
        if (showLoading) _uiState.value = RoutinesUiState.Loading
        viewModelScope.launch {
            repository.listActive().fold(
                onSuccess = { list ->
                    val sorted = sortRoutinesByName(list)
                    _uiState.update { current ->
                        (current as? RoutinesUiState.Content)?.copy(routines = sorted)
                            ?: RoutinesUiState.Content(sorted, isOnline = online)
                    }
                },
                onFailure = { if (showLoading) _uiState.value = RoutinesUiState.Error },
            )
        }
    }

    private fun duplicate(id: String) {
        if (!online) return
        updateContent { it.copy(menuFor = null) }
        viewModelScope.launch {
            repository.duplicate(id).fold(
                onSuccess = { load(showLoading = false) },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    private fun start(create: suspend () -> Result<String>) {
        val content = _uiState.value as? RoutinesUiState.Content ?: return
        if (!online || content.starting) return
        updateContent { it.copy(starting = true) }
        viewModelScope.launch {
            val result = create()
            effectChannel.trySend(
                when {
                    result.isSuccess -> RoutinesEffect.OpenLiveSession
                    result.appErrorOrNull() == AppError.SessionAlreadyInProgress -> RoutinesEffect.SessionInProgress
                    else -> RoutinesEffect.StartFailed
                },
            )
            updateContent { it.copy(starting = false) }
        }
    }

    private fun confirmArchive() {
        val target = (_uiState.value as? RoutinesUiState.Content)?.archiveFor ?: return
        updateContent { it.copy(archiveFor = null) }
        if (!online) return
        viewModelScope.launch {
            repository.archive(target.id).fold(
                onSuccess = { updateContent { content -> content.copy(routines = content.routines - target) } },
                onFailure = { effectChannel.trySend(RoutinesEffect.ActionFailed) },
            )
        }
    }

    private fun RoutinesUiState.Content.find(id: String): RoutineSummary? = routines.firstOrNull { it.id == id }

    private fun updateContent(transform: (RoutinesUiState.Content) -> RoutinesUiState.Content) {
        _uiState.update { if (it is RoutinesUiState.Content) transform(it) else it }
    }
}
