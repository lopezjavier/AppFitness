package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.WorkoutDraft
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Logs a workout of a past date (US-37), empty or prefilled from a routine's plan or from a finished workout used as
 * a template; the source is only read, edits never touch it. The date defaults to yesterday and must not be in the
 * future. One "Guardar" creates a `completed` session (never through the live session); leaving writes nothing. The
 * form lives in [savedState] (ADR-40).
 */
@Suppress("LongParameterList") // Collaborators are injected.
class PastWorkoutViewModel(
    private val source: PastWorkoutSource,
    private val repository: WorkoutSessionRepository,
    private val routineRepository: RoutineRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var terminal: PastWorkoutUiState? = PastWorkoutUiState.Loading
    private var online = true
    private var busy = false

    private val editor = WorkoutDraftEditor(
        savedState = savedState,
        keyPrefix = "past_workout",
        validate = ValidateWorkoutDraft(clock),
        checkDate = true,
        scope = viewModelScope,
        loadLookup = { loadExerciseLookup(exerciseRepository, exerciseLibrary) },
        onChange = ::publish,
    )

    private val _uiState = MutableStateFlow<PastWorkoutUiState>(PastWorkoutUiState.Loading)
    val uiState: StateFlow<PastWorkoutUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<PastWorkoutEffect>(Channel.BUFFERED)
    val effects: Flow<PastWorkoutEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        load()
    }

    fun onEvent(event: PastWorkoutEvent) {
        when (event) {
            PastWorkoutEvent.Retry -> if (terminal == PastWorkoutUiState.Error) load()

            is PastWorkoutEvent.DateChanged -> if (terminal == null && !busy) {
                editor.update { it.copy(date = event.date) }
                publish()
            }

            PastWorkoutEvent.Save -> save()
        }
    }

    fun onEvent(event: WorkoutFormEvent) {
        if (terminal != null || busy) return
        editor.apply(event)
        publish()
    }

    private fun defaultDate(): LocalDate = clock.today().minus(1, DateTimeUnit.DAY)

    private fun load() {
        terminal = PastWorkoutUiState.Loading
        publish()
        viewModelScope.launch {
            val lookup = loadExerciseLookup(exerciseRepository, exerciseLibrary)
            editor.applyLookup(lookup)
            if (editor.restored) {
                terminal = null
            } else {
                initialDraft().fold(
                    onSuccess = {
                        editor.start(it.toForm())
                        terminal = null
                    },
                    onFailure = { terminal = PastWorkoutUiState.Error },
                )
            }
            publish()
        }
    }

    @Suppress("ReturnCount") // One branch per source.
    private suspend fun initialDraft(): Result<WorkoutDraft> {
        val date = defaultDate()
        return when (source) {
            PastWorkoutSource.Empty -> Result.success(
                WorkoutDraft("", null, null, date, null, 0, emptyList()),
            )

            is PastWorkoutSource.FromRoutine -> routineRepository.get(source.routineId).map {
                WorkoutDraftFactory.fromRoutine(it, editor.metricTypes(), date)
            }

            is PastWorkoutSource.FromSession -> repository.getSession(source.sessionId).mapCatching {
                WorkoutDraftFactory.fromSession(checkNotNull(it), date, asTemplate = true)
            }
        }
    }

    @Suppress("ReturnCount") // Guard clauses.
    private fun save() {
        if (terminal != null || busy) return
        if (!online) {
            effectChannel.trySend(PastWorkoutEffect.ActionFailed)
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
            val result = repository.saveCompleted(draft)
            busy = false
            if (result.isFailure) editor.markSubmitAttempted()
            publish()
            effectChannel.trySend(
                result.fold(onSuccess = {
                    PastWorkoutEffect.Saved(it)
                }, onFailure = { PastWorkoutEffect.ActionFailed }),
            )
        }
    }

    private fun publish() {
        _uiState.value = terminal ?: editor.view()?.let { PastWorkoutUiState.Content(it, clock.today(), online, busy) }
            ?: PastWorkoutUiState.Loading
    }
}
