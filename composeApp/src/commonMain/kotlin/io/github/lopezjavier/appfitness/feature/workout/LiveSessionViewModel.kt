package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildSessionSummary
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import io.github.lopezjavier.appfitness.domain.usecase.PendingSetQueue
import io.github.lopezjavier.appfitness.domain.usecase.RestTimer
import io.github.lopezjavier.appfitness.domain.usecase.SetWrite
import io.github.lopezjavier.appfitness.domain.usecase.resolveRest
import io.github.lopezjavier.appfitness.domain.usecase.supersetLabels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant

private const val TICK_MILLIS = 1000L
private const val MAX_REPS = 999
private const val MAX_RETRIES = 3
private const val RETRY_BASE_MILLIS = 1000L

/** One set of the session as the screen edits it. */
private data class SetState(
    val setIndex: Int,
    val setType: SetType,
    val weight: String,
    val reps: String,
    val completedAt: Instant?,
    val weightPlaceholder: String? = null,
    val repsPlaceholder: String? = null,
    val invalid: Boolean = false,
)

private data class ExerciseState(
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int?,
    val sets: List<SetState>,
)

private data class SessionState(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val exercises: List<ExerciseState>,
)

/**
 * The live workout (US-11). Checking a set is optimistic: the row changes at once and the write goes through an
 * in-memory [PendingSetQueue] (last write per set wins) that is flushed whenever the device is online, so no
 * completed set is lost while the screen is alive. Weight and reps are local drafts until the set is checked.
 * Finishing and discarding need the server and are blocked offline; finishing sends the queue first.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One small function per event; collaborators are injected.
class LiveSessionViewModel(
    private val repository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val haptics: Haptics,
    private val routineRepository: RoutineRepository,
    private val alertSound: AlertSound,
) : ViewModel() {
    private var session: SessionState? = null

    /** Rest in seconds per exercise position; positions without an entry use the default. */
    private var rests: MutableMap<Int, Int> = mutableMapOf()

    /** Last performance per exercise id; a missing key means no ghost. */
    private var ghosts: MutableMap<String, List<PreviousSet>> = mutableMapOf()
    private var restTimer: RestTimer? = null
    private var tickerJob: Job? = null

    /** False while the screen is in the background: the rest still resolves, but without sound or vibration. */
    private var visible = true
    private var names: Map<String, String> = emptyMap()
    private var online = true
    private var finishSheet: FinishSheet? = null
    private var discardAsked = false
    private var busy = false
    private var terminal: LiveSessionUiState? = LiveSessionUiState.Loading

    private val queue = PendingSetQueue()
    private val flushLock = Mutex()
    private var syncJob: Job? = null

    /** Sets whose last write has not reached the server (shown as "pendiente de guardar"). */
    private var unsynced: Set<Pair<Int, Int>> = emptySet()

    private val _uiState = MutableStateFlow<LiveSessionUiState>(LiveSessionUiState.Loading)
    val uiState: StateFlow<LiveSessionUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<LiveSessionEffect>(Channel.BUFFERED)
    val effects: Flow<LiveSessionEffect> = effectChannel.receiveAsFlow()

    /** Whole seconds since the session started, refreshed every second while collected (driven by [clock]). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val elapsedSeconds: Flow<Long> = uiState
        .map { (it as? LiveSessionUiState.Active)?.startedAt }
        .distinctUntilChanged()
        .flatMapLatest { startedAt ->
            if (startedAt == null) {
                emptyFlow()
            } else {
                flow {
                    while (true) {
                        emit((clock.now() - startedAt).inWholeSeconds.coerceAtLeast(0))
                        delay(TICK_MILLIS)
                    }
                }
            }
        }
        .distinctUntilChanged()

    init {
        viewModelScope.launch {
            connectivity.isOnline.collect { isOnline ->
                online = isOnline
                publish()
                if (isOnline) sync()
            }
        }
        load()
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: LiveSessionEvent) {
        when (event) {
            LiveSessionEvent.Retry -> if (terminal == LiveSessionUiState.Error) load()

            is LiveSessionEvent.WeightChanged -> editSet(event.position, event.setIndex) {
                it.copy(weight = event.text, invalid = false)
            }

            is LiveSessionEvent.RepsChanged -> editSet(event.position, event.setIndex) {
                it.copy(reps = event.text, invalid = false)
            }

            is LiveSessionEvent.SetChecked -> setChecked(event)

            is LiveSessionEvent.AddSet -> addSet(event.position)

            is LiveSessionEvent.UsePrevious -> usePrevious(event.position, event.setIndex)

            LiveSessionEvent.RestAdd30 -> restAdd30()

            LiveSessionEvent.RestSkip -> stopRest()

            is LiveSessionEvent.SetExerciseRest -> setExerciseRest(event.position, event.seconds)

            LiveSessionEvent.Resume -> {
                visible = true
                tickRest()
            }

            LiveSessionEvent.Pause -> visible = false

            LiveSessionEvent.AddExercise -> effectChannel.trySend(LiveSessionEffect.OpenPicker)

            is LiveSessionEvent.ExerciseSelected -> addExercise(event.exerciseId)

            LiveSessionEvent.RequestFinish -> requestFinish()

            LiveSessionEvent.DismissFinish -> updateDialogs { finishSheet = null }

            LiveSessionEvent.ConfirmFinish -> confirmFinish()

            LiveSessionEvent.RequestDiscard -> requestDiscard()

            LiveSessionEvent.DismissDiscard -> updateDialogs { discardAsked = false }

            LiveSessionEvent.ConfirmDiscard -> confirmDiscard()

            LiveSessionEvent.Done -> effectChannel.trySend(LiveSessionEffect.Closed)
        }
    }

    private fun load() {
        terminal = LiveSessionUiState.Loading
        publish()
        viewModelScope.launch {
            repository.getInProgress().fold(
                onSuccess = { loaded ->
                    if (loaded == null) {
                        terminal = LiveSessionUiState.NoSession
                    } else {
                        loadNames()
                        session = loaded.toState()
                        loadRests(loaded)
                        ghosts = mutableMapOf()
                        coroutineScope {
                            loaded.exercises.map { it.exerciseId }.distinct().map { async { loadGhost(it) } }.awaitAll()
                        }
                        terminal = null
                    }
                },
                onFailure = { terminal = LiveSessionUiState.Error },
            )
            publish()
        }
    }

    /** Catalog names come from the library by key, custom ones from their row; a failure leaves rows unnamed. */
    private suspend fun loadNames() {
        val rows = exerciseRepository.getAll().getOrNull() ?: return
        val library = exerciseLibrary.load()
        names = rows.mapNotNull { row ->
            val name = if (row.isCustom) row.name else row.key?.let { library[it]?.name }
            name?.let { row.id to it }
        }.toMap()
    }

    /**
     * The planned rest of each exercise comes from the routine the session started from, matched by position and
     * exercise; anything unmatched (or a failed read, or a session without a routine) uses the default.
     */
    private suspend fun loadRests(loaded: WorkoutSession) {
        val routine = loaded.routineId?.let { routineRepository.get(it).getOrNull() }
        rests = loaded.exercises.associate { exercise ->
            val planned = routine?.exercises?.firstOrNull {
                it.position == exercise.position && it.exerciseId == exercise.exerciseId
            }
            exercise.position to (planned?.restSeconds ?: RoutineBounds.DEFAULT_REST_SECONDS)
        }.toMutableMap()
    }

    /** A failed read just means no ghost; it never blocks the session. */
    private suspend fun loadGhost(exerciseId: String) {
        repository.lastPerformance(exerciseId).getOrNull()?.takeIf { it.isNotEmpty() }?.let { ghosts[exerciseId] = it }
    }

    private fun ghostFor(exerciseId: String, setIndex: Int): PreviousSet? =
        ghosts[exerciseId]?.let { GhostData.forSet(it, setIndex) }

    private fun usePrevious(position: Int, setIndex: Int) {
        val exercise = session?.exercises?.firstOrNull { it.position == position } ?: return
        val (weight, reps) = GhostData.placeholders(ghostFor(exercise.exerciseId, setIndex)) ?: return
        editSet(position, setIndex) { it.copy(weight = weight, reps = reps, invalid = false) }
    }

    private fun startRest(position: Int) {
        val duration = resolveRest(position, rests) ?: return
        restTimer = RestTimer.start(duration, clock.now())
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (restTimer != null) {
                delay(TICK_MILLIS)
                tickRest()
            }
        }
    }

    /** Recomputes the countdown from the clock; at zero the alert fires once and the timer disappears. */
    private fun tickRest() {
        val timer = restTimer ?: return
        if (timer.isFinished(clock.now())) {
            restTimer = null
            tickerJob?.cancel()
            if (visible) {
                haptics.alert()
                alertSound.play()
            }
        }
        publish()
    }

    private fun restAdd30() {
        restTimer = restTimer?.extend(clock.now())
        publish()
    }

    private fun stopRest() {
        restTimer = null
        tickerJob?.cancel()
        publish()
    }

    private fun setExerciseRest(position: Int, seconds: Int) {
        if (session?.exercises?.none { it.position == position } != false) return
        rests[position] = seconds.coerceIn(RoutineBounds.REST_MIN_SECONDS, RoutineBounds.REST_MAX_SECONDS)
        publish()
    }

    private fun editSet(position: Int, setIndex: Int, change: (SetState) -> SetState) {
        updateSet(position, setIndex) { if (it.completedAt == null) change(it) else it }
        publish()
    }

    private fun setChecked(event: LiveSessionEvent.SetChecked) {
        val set = findSet(event.position, event.setIndex)
        if (set == null || event.checked == (set.completedAt != null)) return
        if (event.checked && parseEntry(set) == null) {
            updateSet(event.position, event.setIndex) { it.copy(invalid = true) }
            publish()
            return
        }
        if (event.checked) haptics.tick()
        val completedAt = if (event.checked) clock.now() else null
        updateSet(event.position, event.setIndex) { it.copy(completedAt = completedAt, invalid = false) }
        write(event.position, event.setIndex)
        if (event.checked) startRest(event.position)
    }

    private fun addSet(position: Int) {
        val current = session ?: return
        val exercise = current.exercises.firstOrNull { it.position == position } ?: return
        val previous = exercise.sets.lastOrNull()
        val added = SetState(
            setIndex = (exercise.sets.maxOfOrNull { it.setIndex } ?: 0) + 1,
            setType = SetType.Working,
            weight = "",
            reps = "",
            completedAt = null,
            weightPlaceholder = previous?.let { it.weight.ifBlank { it.weightPlaceholder } },
            repsPlaceholder = previous?.let { it.reps.ifBlank { it.repsPlaceholder } },
        )
        replaceExercise(position) { it.copy(sets = it.sets + added) }
        write(position, added.setIndex)
    }

    private fun addExercise(exerciseId: String) {
        if (session == null) return
        viewModelScope.launch {
            if (exerciseId !in names) loadNames()
            if (exerciseId !in ghosts) loadGhost(exerciseId)
            val latest = session ?: return@launch
            val position = latest.exercises.size
            rests[position] = RoutineBounds.DEFAULT_REST_SECONDS
            val first = SetState(1, SetType.Working, "", "", null)
            val added = ExerciseState(exerciseId, position, null, listOf(first))
            session = latest.copy(exercises = latest.exercises + added)
            write(position, first.setIndex)
        }
    }

    private fun requestFinish() {
        val current = session ?: return
        if (!online || busy || terminal != null) return
        val pending = current.exercises.sumOf { exercise -> exercise.sets.count { it.completedAt == null } }
        updateDialogs { finishSheet = FinishSheet(pending) }
    }

    private fun requestDiscard() {
        if (!online || busy || terminal != null) return
        updateDialogs { discardAsked = true }
    }

    private fun confirmFinish() {
        val current = session ?: return
        if (finishSheet == null || busy || !online) return
        busy = true
        publish()
        viewModelScope.launch {
            if (!flushQueue()) return@launch fail()
            if (repository.finish(current.id).isFailure) return@launch fail()
            val endedAt = clock.now()
            val finished = current.toWorkoutSession(endedAt)
            val summary = BuildSessionSummary(finished, endedAt, loadHistory(finished))
            val named = current.exercises.associate { it.position to names[it.exerciseId] }
            busy = false
            finishSheet = null
            restTimer = null
            tickerJob?.cancel()
            terminal = LiveSessionUiState.Finished(LiveSummary(summary, named))
            publish()
        }
    }

    private fun confirmDiscard() {
        val current = session ?: return
        if (!discardAsked || busy || !online) return
        busy = true
        publish()
        viewModelScope.launch {
            if (repository.discard(current.id).isFailure) return@launch fail()
            busy = false
            discardAsked = false
            restTimer = null
            tickerJob?.cancel()
            publish()
            effectChannel.trySend(LiveSessionEffect.Closed)
        }
    }

    private fun fail() {
        busy = false
        publish()
        effectChannel.trySend(LiveSessionEffect.ActionFailed)
    }

    /** The history of every exercise with a completed set, or null when any of it cannot be read. */
    private suspend fun loadHistory(finished: WorkoutSession): Map<String, List<ExerciseHistoryPoint>>? {
        val ids = finished.exercises.filter { e -> e.sets.any { it.isCompleted } }.map { it.exerciseId }.distinct()
        val result = mutableMapOf<String, List<ExerciseHistoryPoint>>()
        for (id in ids) {
            result[id] = repository.exerciseHistory(id).getOrElse { return null }
        }
        return result
    }

    /** Queues the current state of a set and sends the queue when online. */
    private fun write(position: Int, setIndex: Int) {
        val current = session
        val exercise = current?.exercises?.firstOrNull { it.position == position }
        val set = exercise?.sets?.firstOrNull { it.setIndex == setIndex }
        if (current == null || exercise == null || set == null) return
        queue.enqueue(
            SetWrite(
                sessionId = current.id,
                exerciseId = exercise.exerciseId,
                exercisePosition = position,
                supersetGroup = exercise.supersetGroup,
                setIndex = setIndex,
                entry = parseEntry(set) ?: SetEntry.WeightReps(0.0, 0),
                completedAt = set.completedAt,
                setType = set.setType,
            ),
        )
        if (online) {
            publish()
            sync()
        } else {
            unsynced = queuedKeys()
            publish()
        }
    }

    /** Flushes the queue; while online a failed flush is retried with a growing delay, a bounded number of times. */
    private fun sync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            var retries = 0
            while (true) {
                val sent = flushQueue()
                publish()
                if (sent || !online || retries >= MAX_RETRIES) break
                delay(RETRY_BASE_MILLIS shl retries)
                retries++
            }
        }
    }

    /** Sends every queued write in order; stops at the first failure. True when the queue is empty afterwards. */
    private suspend fun flushQueue(): Boolean = flushLock.withLock {
        var allSent = true
        for (write in queue.snapshot()) {
            val sent = repository.upsertSet(
                write.sessionId,
                write.exerciseId,
                write.exercisePosition,
                write.supersetGroup,
                write.setIndex,
                write.entry,
                write.completedAt,
                write.setType,
            ).isSuccess
            if (!sent) {
                allSent = false
                break
            }
            queue.remove(write)
        }
        unsynced = queuedKeys()
        allSent && queue.isEmpty
    }

    private fun queuedKeys() = queue.snapshot().map { it.exercisePosition to it.setIndex }.toSet()

    private fun updateDialogs(change: () -> Unit) {
        change()
        publish()
    }

    private fun findSet(position: Int, setIndex: Int): SetState? = session?.exercises
        ?.firstOrNull { it.position == position }?.sets?.firstOrNull { it.setIndex == setIndex }

    private fun updateSet(position: Int, setIndex: Int, change: (SetState) -> SetState) =
        replaceExercise(position) { exercise ->
            exercise.copy(sets = exercise.sets.map { if (it.setIndex == setIndex) change(it) else it })
        }

    private fun replaceExercise(position: Int, change: (ExerciseState) -> ExerciseState) {
        session = session?.let { current ->
            current.copy(exercises = current.exercises.map { if (it.position == position) change(it) else it })
        }
    }

    private fun publish() {
        _uiState.value = terminal ?: render()
    }

    private fun render(): LiveSessionUiState {
        val current = session ?: return LiveSessionUiState.Loading
        val labels = supersetLabels(current.exercises.map { it.supersetGroup })
        return LiveSessionUiState.Active(
            name = current.name,
            startedAt = current.startedAt,
            exercises = current.exercises.mapIndexed { index, exercise ->
                LiveExercise(
                    position = exercise.position,
                    exerciseId = exercise.exerciseId,
                    name = names[exercise.exerciseId],
                    supersetLabel = labels[index],
                    sets = exercise.sets.map { set ->
                        val ghost = ghostFor(exercise.exerciseId, set.setIndex)
                        LiveSetRow(
                            setIndex = set.setIndex,
                            weight = set.weight,
                            reps = set.reps,
                            completed = set.completedAt != null,
                            weightPlaceholder = ghost?.let { GhostData.weightText(it.weightKg) }
                                ?: set.weightPlaceholder,
                            repsPlaceholder = ghost?.reps?.toString() ?: set.repsPlaceholder,
                            previous = GhostData.previousText(ghost),
                            pendingSave = (exercise.position to set.setIndex) in unsynced,
                            invalid = set.invalid,
                        )
                    },
                    restSeconds = rests[exercise.position] ?: RoutineBounds.DEFAULT_REST_SECONDS,
                )
            },
            restTimer = restTimer?.let { RestTimerUi(it.remaining(clock.now()), it.total) },
            isOnline = online,
            finishSheet = finishSheet,
            discardConfirm = discardAsked,
            busy = busy,
        )
    }

    private fun WorkoutSession.toState() = SessionState(
        id = id,
        name = name,
        startedAt = startedAt,
        exercises = exercises.sortedBy { it.position }.map { exercise ->
            ExerciseState(
                exerciseId = exercise.exerciseId,
                position = exercise.position,
                supersetGroup = exercise.supersetGroup,
                sets = exercise.sets.sortedBy { it.setIndex }.map { it.toState() },
            )
        },
    )

    private fun WorkoutSet.toState(): SetState {
        val entry = entry as? SetEntry.WeightReps
        val done = completedAt != null
        return SetState(
            setIndex = setIndex,
            setType = setType,
            weight = entry?.takeIf { done || it.weightKg != 0.0 }?.let { weightText(it.weightKg) }.orEmpty(),
            reps = entry?.takeIf { done || it.reps != 0 }?.reps?.toString().orEmpty(),
            completedAt = completedAt,
        )
    }

    private fun SessionState.toWorkoutSession(endedAt: Instant) = WorkoutSession(
        id = id,
        routineId = null,
        name = name,
        status = SessionStatus.Completed,
        startedAt = startedAt,
        endedAt = endedAt,
        exercises = exercises.map { exercise ->
            SessionExercise(
                exercise.exerciseId,
                exercise.position,
                exercise.supersetGroup,
                exercise.sets.map { set ->
                    WorkoutSet(
                        set.setIndex,
                        set.setType,
                        parseEntry(set) ?: SetEntry.WeightReps(0.0, 0),
                        set.completedAt,
                    )
                },
            )
        },
    )

    /** The set's draft as an entry: reps from 1 to 999 and a weight of zero or more; null when it is not valid. */
    private fun parseEntry(set: SetState): SetEntry.WeightReps? {
        val weight = NumberInputDefaults.parse(set.weight)?.takeIf { it >= 0.0 }
        val reps = set.reps.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS }
        return if (weight != null && reps != null) SetEntry.WeightReps(weight, reps) else null
    }
}
