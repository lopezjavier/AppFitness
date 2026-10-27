package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.repository.warmUpIncludedOrDefault
import io.github.lopezjavier.appfitness.domain.usecase.ComputeSessionStats
import io.github.lopezjavier.appfitness.domain.usecase.DetectSessionPersonalRecords
import io.github.lopezjavier.appfitness.domain.usecase.MuscleSplit
import io.github.lopezjavier.appfitness.domain.usecase.PersonalRecordCatalogue
import io.github.lopezjavier.appfitness.domain.usecase.SaveWorkoutAsRoutine
import io.github.lopezjavier.appfitness.domain.usecase.SetEntryText
import io.github.lopezjavier.appfitness.domain.usecase.perHandIdsOrDefault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val KEY_ROUTINE_OPEN = "detail_save_routine_open"

/**
 * Detail of a completed session (US-14): sets, aggregates and personal records computed on the client, per-set
 * correction and deletion. Editing and deleting need the server and are ignored offline.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Collaborators are injected; one function per event.
class SessionDetailViewModel(
    private val sessionId: String,
    private val repository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val preferences: TrainingPreferencesRepository,
    private val bodyMeasurements: BodyMeasurementRepository,
    private val routineRepository: RoutineRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var session: WorkoutSession? = null
    private var names: Map<String, String> = emptyMap()

    /** Primary muscle group by exercise id; empty when the exercises could not be read (no split). */
    private var groups: Map<String, MuscleGroup> = emptyMap()

    /** Latest body weight for the bodyweight and assisted volume; null when unknown. */
    private var bodyWeightKg: Double? = null

    /** Per-hand exercises (volume counts twice); empty when none or unreadable. */
    private var perHandIds: Set<String> = emptySet()

    /** History of every exercise of the session; null when it could not be read (no records are shown). */
    private var history: Map<String, List<ExerciseHistoryPoint>>? = null

    /** Record aggregates of every exercise of the session; null when they could not be read (no marks). */
    private var recordHistory: Map<String, List<ExerciseSessionRecords>>? = null
    private var terminal: SessionDetailUiState? = SessionDetailUiState.Loading
    private var online = true
    private var includeWarmUp = false
    private var busy = false

    /** The "Guardar como rutina" sheet: the open flag survives recreation (ADR-40); the typed name is the sheet's. */
    private var routineSheetOpen = savedState.get<Boolean>(KEY_ROUTINE_OPEN) ?: false
    private var existingRoutineNames: List<String> = emptyList()
    private var routineBusy = false
    private var routineFailed = false

    private val _uiState = MutableStateFlow<SessionDetailUiState>(SessionDetailUiState.Loading)
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<SessionDetailEffect>(Channel.BUFFERED)
    val effects: Flow<SessionDetailEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect {
                online = it
                publish()
            }
        }
        load()
        if (routineSheetOpen) loadRoutineNames()
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: SessionDetailEvent) {
        when (event) {
            SessionDetailEvent.Retry -> if (terminal == SessionDetailUiState.Error) load()

            SessionDetailEvent.Refresh -> if (terminal == null && !busy) load(showLoading = false)

            SessionDetailEvent.OpenSaveAsRoutine -> if (session != null && !routineSheetOpen) {
                setRoutineSheet(open = true)
                loadRoutineNames()
            }

            is SessionDetailEvent.ConfirmSaveAsRoutine -> confirmSaveAsRoutine(event.name)

            SessionDetailEvent.DismissSaveAsRoutine -> if (!routineBusy) {
                routineFailed = false
                setRoutineSheet(open = false)
            }

            SessionDetailEvent.Delete -> if (!busy && session != null) {
                if (online) delete() else effectChannel.trySend(SessionDetailEffect.ActionFailed)
            }
        }
    }

    private fun load(showLoading: Boolean = true) {
        if (showLoading) {
            terminal = SessionDetailUiState.Loading
            publish()
        }
        viewModelScope.launch {
            repository.getSession(sessionId).fold(
                onSuccess = { loaded ->
                    if (loaded == null) {
                        terminal = SessionDetailUiState.Error
                    } else {
                        session = loaded
                        val lookup = loadExerciseLookup(exerciseRepository, exerciseLibrary)
                        names = lookup?.names ?: names
                        groups = lookup?.groups ?: groups
                        includeWarmUp = preferences.warmUpIncludedOrDefault()
                        bodyWeightKg = bodyMeasurements.getLatestWeight().getOrNull()?.weightKg
                        perHandIds = exerciseRepository.perHandIdsOrDefault()
                        history = loadHistory(loaded)
                        recordHistory = loadRecordHistory(loaded)
                        terminal = null
                    }
                },
                // A silent refresh keeps what is on screen when the reload fails.
                onFailure = { if (showLoading) terminal = SessionDetailUiState.Error },
            )
            publish()
        }
    }

    /** The history of every exercise, or null when any of it cannot be read: it never blocks the detail. */
    private suspend fun loadHistory(loaded: WorkoutSession): Map<String, List<ExerciseHistoryPoint>>? = coroutineScope {
        val ids = loaded.exercises.map { it.exerciseId }.distinct()
        val results = ids.map { id -> async { repository.exerciseHistory(id).getOrNull() } }.awaitAll()
        if (results.any { it == null }) null else ids.zip(results.filterNotNull()).toMap()
    }

    /** The record aggregates of every exercise, or null when any of them cannot be read. */
    private suspend fun loadRecordHistory(loaded: WorkoutSession): Map<String, List<ExerciseSessionRecords>>? =
        coroutineScope {
            val ids = loaded.exercises.map { it.exerciseId }.distinct()
            val results = ids.map { id -> async { repository.sessionRecords(id).getOrNull() } }.awaitAll()
            if (results.any { it == null }) null else ids.zip(results.filterNotNull()).toMap()
        }

    private fun setRoutineSheet(open: Boolean) {
        routineSheetOpen = open
        savedState[KEY_ROUTINE_OPEN] = open
        if (!open) existingRoutineNames = emptyList()
        publish()
    }

    /** The active routines' names for the sheet's unique-name check; a failure leaves it empty. */
    private fun loadRoutineNames() {
        viewModelScope.launch {
            existingRoutineNames = routineRepository.listActive().getOrNull().orEmpty().map { it.name }
            publish()
        }
    }

    private fun tooManySets() = SaveWorkoutAsRoutine.exceedsSetLimit(
        session?.exercises?.map { exercise -> exercise.sets.count { it.isCompleted } }.orEmpty(),
    )

    @Suppress("ReturnCount") // Guard clauses.
    private fun confirmSaveAsRoutine(name: String) {
        if (!routineSheetOpen || routineBusy) return
        if (!SaveWorkoutAsRoutine.isValidName(name) || tooManySets()) return
        if (!online) {
            routineFailed = true
            publish()
            return
        }
        routineBusy = true
        routineFailed = false
        publish()
        viewModelScope.launch {
            val result = routineRepository.createFromSession(sessionId, name)
            routineBusy = false
            if (result.isSuccess) {
                setRoutineSheet(open = false)
                effectChannel.trySend(SessionDetailEffect.SavedAsRoutine(result.getOrThrow()))
            } else {
                routineFailed = true
                publish()
            }
        }
    }

    private fun delete() {
        busy = true
        publish()
        viewModelScope.launch {
            val result = repository.deleteSession(sessionId)
            busy = false
            publish()
            effectChannel.trySend(
                if (result.isSuccess) SessionDetailEffect.Deleted else SessionDetailEffect.ActionFailed,
            )
        }
    }

    private fun publish() {
        _uiState.value = terminal ?: render()
    }

    private fun render(): SessionDetailUiState {
        val current = session ?: return SessionDetailUiState.Loading
        val records = history?.let { DetectSessionPersonalRecords(current, it, includeWarmUp) }.orEmpty()
        val beaten = PersonalRecordCatalogue.beatenIn(
            current,
            recordHistory,
            includeWarmUp,
            bodyWeightKg,
            perHandIds,
        )
        return SessionDetailUiState.Content(
            name = current.name,
            date = sessionLocalDate(current.startedAt),
            today = clock.today(),
            stats = ComputeSessionStats(current, includeWarmUp, bodyWeightKg, perHandIds),
            exercises = current.exercises.sortedBy { it.position }.map { exercise ->
                DetailExercise(
                    position = exercise.position,
                    exerciseId = exercise.exerciseId,
                    name = names[exercise.exerciseId],
                    sets = exercise.sets.sortedBy { it.setIndex }.filter { it.isCompleted }.map { set ->
                        DetailSet(set.setIndex, set.setType, SetEntryText.format(set.entry), set.rpe)
                    },
                    isPersonalRecord = exercise.exerciseId in records,
                    records = beaten[exercise.exerciseId].orEmpty(),
                )
            },
            isOnline = online,
            saveAsRoutine = if (routineSheetOpen) {
                SaveAsRoutineState(existingRoutineNames, tooManySets(), routineBusy, routineFailed)
            } else {
                null
            },
            busy = busy,
            split = MuscleSplit(current, groups, includeWarmUp),
        )
    }
}
