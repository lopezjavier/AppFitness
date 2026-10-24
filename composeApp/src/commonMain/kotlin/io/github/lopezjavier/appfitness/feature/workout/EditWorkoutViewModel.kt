package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.ValidateWorkoutDraft
import io.github.lopezjavier.appfitness.domain.usecase.WorkoutDraftFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Edit screen of a finished workout (US-37): name, description, gym, duration, rests, exercises and sets, with one
 * "Guardar" (ADR-45: nothing is written per action; leaving without saving writes nothing). The form lives in
 * [savedState] (ADR-40). The date is not editable. Saving and deleting need the server and are refused offline with
 * [EditWorkoutEffect.ActionFailed].
 */
@Suppress("LongParameterList") // Collaborators are injected.
class EditWorkoutViewModel(
    private val sessionId: String,
    private val repository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    connectivity: ConnectivityObserver,
    clock: Clock,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var terminal: EditWorkoutUiState? = EditWorkoutUiState.Loading
    private var online = true
    private var busy = false

    private val editor = WorkoutDraftEditor(
        savedState = savedState,
        keyPrefix = "edit_workout",
        validate = ValidateWorkoutDraft(clock),
        checkDate = false,
        scope = viewModelScope,
        loadLookup = { loadExerciseLookup(exerciseRepository, exerciseLibrary) },
        onChange = ::publish,
    )

    private val _uiState = MutableStateFlow<EditWorkoutUiState>(EditWorkoutUiState.Loading)
    val uiState: StateFlow<EditWorkoutUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<EditWorkoutEffect>(Channel.BUFFERED)
    val effects: Flow<EditWorkoutEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        load()
    }

    fun onEvent(event: EditWorkoutEvent) {
        when (event) {
            EditWorkoutEvent.Retry -> if (terminal == EditWorkoutUiState.Error) load()
            EditWorkoutEvent.Save -> save()
            EditWorkoutEvent.Delete -> delete()
        }
    }

    fun onEvent(event: WorkoutFormEvent) {
        if (terminal != null || busy) return
        editor.apply(event)
        publish()
    }

    private fun load() {
        terminal = EditWorkoutUiState.Loading
        publish()
        viewModelScope.launch {
            repository.getSession(sessionId).fold(
                onSuccess = { loaded ->
                    if (loaded == null) {
                        terminal = EditWorkoutUiState.Error
                    } else {
                        editor.applyLookup(loadExerciseLookup(exerciseRepository, exerciseLibrary))
                        val draft = WorkoutDraftFactory.fromSession(loaded, sessionLocalDate(loaded.startedAt))
                        editor.start(draft.toForm())
                        terminal = null
                    }
                },
                onFailure = { terminal = EditWorkoutUiState.Error },
            )
            publish()
        }
    }

    @Suppress("ReturnCount") // Guard clauses.
    private fun save() {
        if (terminal != null || busy) return
        if (!online) {
            effectChannel.trySend(EditWorkoutEffect.ActionFailed)
            return
        }
        val draft = editor.draft() ?: return
        if (editor.issues().isNotEmpty()) {
            editor.markSubmitAttempted()
            publish()
            return
        }
        busy = true
        publish()
        viewModelScope.launch {
            val result = repository.saveCompleted(draft, sessionId)
            busy = false
            if (result.isFailure) editor.markSubmitAttempted()
            publish()
            effectChannel.trySend(if (result.isSuccess) EditWorkoutEffect.Saved else EditWorkoutEffect.ActionFailed)
        }
    }

    private fun delete() {
        if (terminal != null || busy) return
        if (!online) {
            effectChannel.trySend(EditWorkoutEffect.ActionFailed)
            return
        }
        busy = true
        publish()
        viewModelScope.launch {
            val result = repository.deleteSession(sessionId)
            busy = false
            publish()
            effectChannel.trySend(if (result.isSuccess) EditWorkoutEffect.Deleted else EditWorkoutEffect.ActionFailed)
        }
    }

    private fun publish() {
        _uiState.value = terminal ?: editor.view()?.let { EditWorkoutUiState.Content(it, online, busy) }
            ?: EditWorkoutUiState.Loading
    }
}
