package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.ComputeSessionStats
import io.github.lopezjavier.appfitness.domain.usecase.DetectSessionPersonalRecords
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

private const val MAX_REPS = 999

private const val KEY_EDIT_POSITION = "detail_edit_position"
private const val KEY_EDIT_SET = "detail_edit_set"
private const val KEY_EDIT_WEIGHT = "detail_edit_weight"
private const val KEY_EDIT_REPS = "detail_edit_reps"

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
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var session: WorkoutSession? = null
    private var names: Map<String, String> = emptyMap()

    /** History of every exercise of the session; null when it could not be read (no records are shown). */
    private var history: Map<String, List<ExerciseHistoryPoint>>? = null
    private var terminal: SessionDetailUiState? = SessionDetailUiState.Loading
    private var online = true
    private var edit: EditSetState? = null
        set(value) {
            field = value
            persistEdit(value)
        }
    private var deleteConfirm = false
    private var busy = false

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
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: SessionDetailEvent) {
        when (event) {
            SessionDetailEvent.Retry -> if (terminal == SessionDetailUiState.Error) load()

            is SessionDetailEvent.EditSet -> if (online && !busy) openEdit(event.position, event.setIndex)

            is SessionDetailEvent.WeightChanged -> changeEdit { it.copy(weight = event.text, invalid = false) }

            is SessionDetailEvent.RepsChanged -> changeEdit { it.copy(reps = event.text, invalid = false) }

            SessionDetailEvent.SaveSet -> if (!busy) {
                if (online) {
                    saveSet()
                } else if (edit != null) {
                    effectChannel.trySend(SessionDetailEffect.ActionFailed)
                }
            }

            SessionDetailEvent.DismissEdit -> if (!busy) {
                edit = null
                publish()
            }

            SessionDetailEvent.RequestDelete -> if (online && !busy && session != null) {
                deleteConfirm = true
                publish()
            }

            SessionDetailEvent.ConfirmDelete -> if (!busy && deleteConfirm) {
                if (online) delete() else effectChannel.trySend(SessionDetailEffect.ActionFailed)
            }

            SessionDetailEvent.DismissDelete -> if (!busy) {
                deleteConfirm = false
                publish()
            }
        }
    }

    private fun load() {
        terminal = SessionDetailUiState.Loading
        publish()
        viewModelScope.launch {
            repository.getSession(sessionId).fold(
                onSuccess = { loaded ->
                    if (loaded == null) {
                        terminal = SessionDetailUiState.Error
                    } else {
                        session = loaded
                        names = loadExerciseNames(exerciseRepository, exerciseLibrary) ?: names
                        history = loadHistory(loaded)
                        restoreEdit(loaded)
                        terminal = null
                    }
                },
                onFailure = { terminal = SessionDetailUiState.Error },
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

    private fun openEdit(position: Int, setIndex: Int) {
        val set = session?.exercises?.firstOrNull { it.position == position }
            ?.sets?.firstOrNull { it.setIndex == setIndex } ?: return
        val entry = set.entry as? SetEntry.WeightReps ?: return
        edit = EditSetState(position, setIndex, weightText(entry.weightKg), entry.reps.toString())
        publish()
    }

    /** A correction typed before process death comes back, still unsaved, once the session it targets is loaded. */
    private fun restoreEdit(loaded: WorkoutSession) {
        val position = savedState.get<Int>(KEY_EDIT_POSITION)
        val setIndex = savedState.get<Int>(KEY_EDIT_SET)
        val exists = loaded.exercises.firstOrNull { it.position == position }?.sets?.any { it.setIndex == setIndex }
        if (position == null || setIndex == null || exists != true) return
        edit = EditSetState(
            position,
            setIndex,
            savedState.get<String>(KEY_EDIT_WEIGHT).orEmpty(),
            savedState.get<String>(KEY_EDIT_REPS).orEmpty(),
        )
    }

    private fun persistEdit(value: EditSetState?) {
        savedState[KEY_EDIT_POSITION] = value?.position
        savedState[KEY_EDIT_SET] = value?.setIndex
        savedState[KEY_EDIT_WEIGHT] = value?.weight
        savedState[KEY_EDIT_REPS] = value?.reps
    }

    private fun changeEdit(change: (EditSetState) -> EditSetState) {
        edit = edit?.let(change)
        publish()
    }

    @Suppress("ReturnCount") // Guard clauses.
    private fun saveSet() {
        val current = session ?: return
        val draft = edit ?: return
        val entry = parseEntry(draft)
        if (entry == null) {
            edit = draft.copy(invalid = true)
            publish()
            return
        }
        val exercise = current.exercises.firstOrNull { it.position == draft.position } ?: return
        val original = exercise.sets.firstOrNull { it.setIndex == draft.setIndex } ?: return
        busy = true
        publish()
        viewModelScope.launch {
            val result = repository.upsertSet(
                sessionId = current.id,
                exerciseId = exercise.exerciseId,
                exercisePosition = exercise.position,
                supersetGroup = exercise.supersetGroup,
                setIndex = original.setIndex,
                entry = entry,
                completedAt = original.completedAt,
                setType = original.setType,
            )
            busy = false
            if (result.isSuccess) {
                session = current.withEntry(draft.position, draft.setIndex, entry)
                edit = null
                publish()
            } else {
                publish()
                effectChannel.trySend(SessionDetailEffect.ActionFailed)
            }
        }
    }

    private fun delete() {
        busy = true
        publish()
        viewModelScope.launch {
            val result = repository.deleteSession(sessionId)
            busy = false
            deleteConfirm = false
            publish()
            effectChannel.trySend(
                if (result.isSuccess) SessionDetailEffect.Deleted else SessionDetailEffect.ActionFailed,
            )
        }
    }

    private fun WorkoutSession.withEntry(position: Int, setIndex: Int, entry: SetEntry.WeightReps) = copy(
        exercises = exercises.map { exercise ->
            if (exercise.position != position) {
                exercise
            } else {
                exercise.copy(sets = exercise.sets.map { if (it.setIndex == setIndex) it.copy(entry = entry) else it })
            }
        },
    )

    /** Reps from 1 to 999 and a weight of zero or more; null when the draft is not valid. */
    private fun parseEntry(draft: EditSetState): SetEntry.WeightReps? {
        val weight = NumberInputDefaults.parse(draft.weight)?.takeIf { it >= 0.0 }
        val reps = draft.reps.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS }
        return if (weight != null && reps != null) SetEntry.WeightReps(weight, reps) else null
    }

    private fun publish() {
        _uiState.value = terminal ?: render()
    }

    private fun render(): SessionDetailUiState {
        val current = session ?: return SessionDetailUiState.Loading
        val records = history?.let { DetectSessionPersonalRecords(current, it) }.orEmpty()
        return SessionDetailUiState.Content(
            name = current.name,
            label = sessionDateLabel(sessionLocalDate(current.startedAt), clock.today()),
            stats = ComputeSessionStats(current),
            exercises = current.exercises.sortedBy { it.position }.map { exercise ->
                DetailExercise(
                    position = exercise.position,
                    exerciseId = exercise.exerciseId,
                    name = names[exercise.exerciseId],
                    sets = exercise.sets.sortedBy { it.setIndex }.mapNotNull { set ->
                        (set.entry as? SetEntry.WeightReps)?.takeIf { set.isCompleted }
                            ?.let { DetailSet(set.setIndex, set.setType, it.weightKg, it.reps) }
                    },
                    isPersonalRecord = exercise.exerciseId in records,
                )
            },
            isOnline = online,
            edit = edit,
            deleteConfirm = deleteConfirm,
            busy = busy,
        )
    }
}
