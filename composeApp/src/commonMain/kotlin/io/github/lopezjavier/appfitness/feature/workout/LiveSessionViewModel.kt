package io.github.lopezjavier.appfitness.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.designsystem.NumberInputDefaults
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.PlannedSet
import io.github.lopezjavier.appfitness.domain.model.PreviousSet
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.domain.model.SessionExercise
import io.github.lopezjavier.appfitness.domain.model.SessionStatus
import io.github.lopezjavier.appfitness.domain.model.SetColumns
import io.github.lopezjavier.appfitness.domain.model.SetEntry
import io.github.lopezjavier.appfitness.domain.model.SetType
import io.github.lopezjavier.appfitness.domain.model.TrainingPreferences
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet
import io.github.lopezjavier.appfitness.domain.model.WorkoutSession
import io.github.lopezjavier.appfitness.domain.model.WorkoutSet
import io.github.lopezjavier.appfitness.domain.model.columns
import io.github.lopezjavier.appfitness.domain.model.empty
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.port.Haptics
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseOptionsRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.repository.doubleDumbbellIdsOrDefault
import io.github.lopezjavier.appfitness.domain.repository.warmUpIncludedOrDefault
import io.github.lopezjavier.appfitness.domain.usecase.BestPastWeight
import io.github.lopezjavier.appfitness.domain.usecase.BuildSessionSummary
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.PendingSetQueue
import io.github.lopezjavier.appfitness.domain.usecase.RestTimer
import io.github.lopezjavier.appfitness.domain.usecase.RpeScale
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.SessionDuration
import io.github.lopezjavier.appfitness.domain.usecase.SetWrite
import io.github.lopezjavier.appfitness.domain.usecase.Stopwatch
import io.github.lopezjavier.appfitness.domain.usecase.WarmUpCalculator
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val TICK_MILLIS = 1000L
private const val MAX_REPS = 999
private const val MAX_RETRIES = 3
private const val RETRY_BASE_MILLIS = 1000L

private const val KEY_PICKER_POSITION = "live_picker_position"
private const val KEY_PICKER_SET = "live_picker_set"
private const val KEY_RPE_PICKER_POSITION = "live_rpe_picker_position"
private const val KEY_RPE_PICKER_SET = "live_rpe_picker_set"
private const val KEY_RPE_PICKER_COMPLETES = "live_rpe_picker_completes"
private const val KEY_DRAFTS = "live_drafts"
private const val KEY_PAUSE_KNOWN = "live_pause_known"
private const val KEY_PAUSED_AT = "live_paused_at"
private const val KEY_PAUSED_TOTAL = "live_paused_total"
private const val KEY_REST_PAUSED_REMAINING = "live_rest_paused_remaining"
private const val KEY_REST_PAUSED_TOTAL = "live_rest_paused_total"
private const val KEY_DOUBLE_DRAFT_EXERCISE = "live_double_dumbbell_exercise"
private const val KEY_DOUBLE_DRAFT_ENABLED = "live_double_dumbbell_enabled"
private const val KEY_BODY_WEIGHT_DRAFT = "live_body_weight_draft"
private const val DRAFT_SEPARATOR = ''
private const val DRAFT_FIELDS = 9
private const val METRES_PER_KM = 1000.0

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
    val rpe: Double? = null,
    val distance: String = "",
    val steps: String = "",
    val floors: String = "",
    val stopwatch: Stopwatch = Stopwatch(),
) {
    /** Something was typed or timed: the set has a draft worth keeping. */
    val hasDraft: Boolean
        get() = weight.isNotEmpty() || reps.isNotEmpty() || distance.isNotEmpty() || steps.isNotEmpty() ||
            floors.isNotEmpty() || stopwatch != Stopwatch()
}

/** What the screen needs to know about an exercise's kind: how its sets are measured and its equipment. */
private data class ExerciseTraits(val metricType: ExerciseMetricType, val equipment: Equipment)

private data class ExerciseState(
    val exerciseId: String,
    val position: Int,
    val supersetGroup: Int?,
    val sets: List<SetState>,
)

private data class SessionState(
    val id: String,
    val routineId: String?,
    val name: String,
    val startedAt: Instant,
    val exercises: List<ExerciseState>,
    val pausedAt: Instant? = null,
    val pausedTotalSeconds: Long = 0,
)

/**
 * A set deletion waiting for the server. [before] are the queued writes taken from the [PendingSetQueue] when it was
 * requested: they use the set indexes the server still has, so they are sent first, then the deletion.
 */
private class DeleteOp(
    val sessionId: String,
    val position: Int,
    val setIndex: Int,
    val before: MutableList<SetWrite>,
) {
    var reported = false
}

/** Where [key] ends up once the set ([position], [setIndex]) is removed: later sets move down, that one is gone. */
private fun shiftedBy(key: Pair<Int, Int>, position: Int, setIndex: Int): Pair<Int, Int>? = when {
    key.first != position || key.second < setIndex -> key
    key.second == setIndex -> null
    else -> key.first to key.second - 1
}

/**
 * The live workout (US-11). Checking a set is optimistic: the row changes at once and the write goes through an
 * in-memory [PendingSetQueue] (last write per set wins) that is flushed whenever the device is online, so no
 * completed set is lost while the screen is alive. Weight and reps are local drafts until the set is checked.
 * Finishing and discarding need the server and are blocked offline; finishing sends the queue first.
 */
// One small function per event; collaborators are injected.
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class LiveSessionViewModel(
    private val repository: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    connectivity: ConnectivityObserver,
    private val clock: Clock,
    private val haptics: Haptics,
    private val routineRepository: RoutineRepository,
    private val alertSound: AlertSound,
    private val preferences: TrainingPreferencesRepository,
    private val bodyMeasurements: BodyMeasurementRepository,
    private val exerciseOptions: ExerciseOptionsRepository,
    private val saveBodyMeasurement: SaveBodyMeasurement,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var session: SessionState? = null

    /** Rest in seconds per exercise position; positions without an entry use the default. */
    private var rests: MutableMap<Int, Int> = mutableMapOf()

    /** Planned sets of the session's routine by (position, setIndex); empty when the routine is gone or unread. */
    private var planned: Map<Pair<Int, Int>, PlannedSet> = emptyMap()

    /** Target RPE of the session's routine by exercise position; empty for free sessions. */
    private var targetRpes: Map<Int, Double> = emptyMap()

    /** Training preferences (read at load, the defaults until then): previous-values mode and calculator settings. */
    private var trainingPreferences = TrainingPreferences()
    private val previousMode get() = trainingPreferences.previousValuesMode

    /** Set deletions waiting for the server, oldest first. */
    private val deleteOps = ArrayDeque<DeleteOp>()

    /** Last performance per exercise id; a missing key means no ghost. */
    private var ghosts: MutableMap<String, List<PreviousSet>> = mutableMapOf()
    private var restTimer: RestTimer? = null
    private var tickerJob: Job? = null

    /** False while the screen is in the background: the rest still resolves, but without sound or vibration. */
    private var visible = true
    private var names: Map<String, String> = emptyMap()
    private var traits: Map<String, ExerciseTraits> = emptyMap()
    private var stopwatchJob: Job? = null
    private var online = true
    private var finishSheet: FinishSheet? = null
    private var discardAsked = false
    private var busy = false

    /** The set whose type picker is open; mirrored in [savedState] so it survives process death. */
    private var typePicker: TypePickerTarget? = restoredPicker()
    private var rpePicker: RpePickerTarget? = restoredRpePicker()
    private var terminal: LiveSessionUiState? = LiveSessionUiState.Loading

    /** Volume options (US-32): exercises counted double, latest body weight and whether today's weight is synced. */
    private var doubleDumbbellIds: Set<String> = emptySet()
    private var bodyWeightKg: Double? = null
    private var todayHasHealthConnectWeight = false
    private var doubleDumbbellDraft: DoubleDumbbellDraft? = restoredDoubleDumbbellDraft()
    private var bodyWeightDraft: String? = savedState.get<String>(KEY_BODY_WEIGHT_DRAFT)
    private var bodyWeightInvalid = false
    private var calculatorDraft: CalculatorDraft? = savedState.restoredCalculatorDraft()

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
        .map { state ->
            (state as? LiveSessionUiState.Active)?.let { Triple(it.startedAt, it.pausedAt, it.pausedTotalSeconds) }
        }
        .distinctUntilChanged()
        .flatMapLatest { timing ->
            if (timing == null) {
                emptyFlow()
            } else {
                val (startedAt, pausedAt, pausedTotal) = timing
                flow {
                    do {
                        emit(SessionDuration.seconds(startedAt, null, pausedAt, pausedTotal, clock.now()))
                        // Paused: the value is constant, so there is nothing to refresh until the state changes.
                        if (pausedAt == null) delay(TICK_MILLIS)
                    } while (pausedAt == null)
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

    @Suppress("CyclomaticComplexMethod", "LongMethod") // A flat dispatch over the events.
    fun onEvent(event: LiveSessionEvent) {
        when (event) {
            LiveSessionEvent.Retry -> if (terminal == LiveSessionUiState.Error) load()

            is LiveSessionEvent.OpenTypePicker -> openTypePicker(event.position, event.setIndex)

            LiveSessionEvent.DismissTypePicker -> setPicker(null)

            is LiveSessionEvent.SetTypeChosen -> chooseType(event)

            is LiveSessionEvent.OpenRpePicker -> openRpePicker(event.position, event.setIndex)

            LiveSessionEvent.DismissRpePicker -> setRpePicker(null)

            is LiveSessionEvent.SetRpeChosen -> chooseRpe(event)

            is LiveSessionEvent.WeightChanged -> editSet(event.position, event.setIndex) {
                it.copy(weight = event.text, invalid = false)
            }

            is LiveSessionEvent.RepsChanged -> editSet(event.position, event.setIndex) {
                it.copy(reps = event.text, invalid = false)
            }

            is LiveSessionEvent.DistanceChanged -> editSet(event.position, event.setIndex) {
                it.copy(distance = event.text, invalid = false)
            }

            is LiveSessionEvent.StepsChanged -> editSet(event.position, event.setIndex) {
                it.copy(steps = event.text, invalid = false)
            }

            is LiveSessionEvent.FloorsChanged -> editSet(event.position, event.setIndex) {
                it.copy(floors = event.text, invalid = false)
            }

            is LiveSessionEvent.StopwatchToggled -> toggleStopwatch(event.position, event.setIndex)

            is LiveSessionEvent.DurationEdited -> editSet(event.position, event.setIndex) {
                it.copy(stopwatch = it.stopwatch.withElapsedSeconds(event.seconds), invalid = false)
            }

            is LiveSessionEvent.SetChecked -> setChecked(event)

            is LiveSessionEvent.AddSet -> addSet(event.position)

            is LiveSessionEvent.DeleteSet -> deleteSet(event.position, event.setIndex)

            LiveSessionEvent.PauseSession -> pauseSession()

            LiveSessionEvent.ResumeSession -> resumeSession()

            is LiveSessionEvent.UsePrevious -> usePrevious(event.position, event.setIndex)

            LiveSessionEvent.RestAdd30 -> restAdd30()

            LiveSessionEvent.RestSkip -> stopRest()

            is LiveSessionEvent.SetExerciseRest -> setExerciseRest(event.position, event.seconds)

            LiveSessionEvent.Resume -> {
                visible = true
                tickRest()
                publish()
                ensureStopwatchTicker()
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

            is LiveSessionEvent.DoubleDumbbellDraftChanged -> updateDialogs {
                doubleDumbbellDraft = DoubleDumbbellDraft(event.exerciseId, event.enabled)
            }

            is LiveSessionEvent.SetDoubleDumbbell -> saveDoubleDumbbell(event.exerciseId, event.enabled)

            is LiveSessionEvent.BodyWeightDraftChanged -> updateDialogs {
                bodyWeightDraft = event.text
                bodyWeightInvalid = false
            }

            is LiveSessionEvent.SaveBodyWeight -> saveBodyWeight(event.text)

            LiveSessionEvent.DismissVolumeDrafts -> updateDialogs {
                doubleDumbbellDraft = null
                bodyWeightDraft = null
                bodyWeightInvalid = false
            }

            is LiveSessionEvent.OpenCalculator -> openCalculator(event.position, event.kind)

            is LiveSessionEvent.CalculatorWeightChanged -> updateDialogs {
                calculatorDraft = calculatorDraft?.copy(weightText = event.text, weightEdited = true)
            }

            is LiveSessionEvent.CalculatorBarChanged -> updateDialogs {
                // The warm-up bar is a preference (ADR-56); only the plate calculator still picks one.
                if (calculatorDraft?.kind == CalculatorKind.Plates && event.barKg in CalculatorSettings.BARS_KG &&
                    !isDumbbell(calculatorDraft?.position)
                ) {
                    calculatorDraft = calculatorDraft?.copy(barKg = event.barKg)
                }
            }

            is LiveSessionEvent.CalculatorWarmUpSetsChanged -> updateDialogs {
                if (event.sets in 1..WarmUpCalculator.MAX_SETS) {
                    calculatorDraft = calculatorDraft?.copy(warmUpSets = event.sets)
                }
            }

            is LiveSessionEvent.AddWarmUpSets -> addWarmUpSets(event.position, event.sets)

            LiveSessionEvent.DismissCalculator -> updateDialogs { calculatorDraft = null }

            LiveSessionEvent.Done -> effectChannel.trySend(LiveSessionEffect.Closed)
        }
    }

    /** Reads the options, the latest weight and today's Health Connect weight; a failed read leaves the defaults. */
    private suspend fun loadVolumeOptions() {
        doubleDumbbellIds = exerciseOptions.doubleDumbbellIdsOrDefault()
        bodyWeightKg = bodyMeasurements.getLatestWeight().getOrNull()?.weightKg
        val today = clock.now().toLocalDateTime(timeZone).date
        val rows = bodyMeasurements.getMeasurements(
            today.atStartOfDayIn(timeZone),
            today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone),
        ).getOrNull().orEmpty()
        todayHasHealthConnectWeight = MeasurementsByDay()(rows, timeZone)[today]?.healthConnect.orEmpty().isNotEmpty()
    }

    private fun saveDoubleDumbbell(exerciseId: String, enabled: Boolean) {
        if (session == null) return
        viewModelScope.launch {
            if (exerciseOptions.setDoubleDumbbell(exerciseId, enabled).isFailure) {
                effectChannel.trySend(LiveSessionEffect.OptionSaveFailed)
                return@launch
            }
            doubleDumbbellIds = if (enabled) doubleDumbbellIds + exerciseId else doubleDumbbellIds - exerciseId
            doubleDumbbellDraft = null
            publish()
        }
    }

    /** Stores [text] as today's manual weight ([SaveBodyMeasurement]); blocked when Health Connect owns today. */
    private fun saveBodyWeight(text: String) {
        if (session == null || todayHasHealthConnectWeight) return
        val weight = NumberInputDefaults.parse(text)
        if (weight == null) {
            updateDialogs {
                bodyWeightDraft = text
                bodyWeightInvalid = true
            }
            return
        }
        viewModelScope.launch {
            val result = saveBodyMeasurement(weight, null)
            if (result.appErrorOrNull() == AppError.Validation) {
                bodyWeightDraft = text
                bodyWeightInvalid = true
                publish()
            } else if (result.isFailure) {
                effectChannel.trySend(LiveSessionEffect.OptionSaveFailed)
            } else {
                bodyWeightKg = weight
                bodyWeightDraft = null
                bodyWeightInvalid = false
                publish()
            }
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
                        loadVolumeOptions()
                        trainingPreferences = preferences.get().getOrNull() ?: TrainingPreferences()
                        session = loaded.toState().withRestoredDrafts().withRestoredPause()
                        restoreFrozenRest()
                        calculatorDraft =
                            calculatorDraft?.takeIf { draft -> loaded.exercises.any { it.position == draft.position } }
                        typePicker = typePicker?.takeIf { findSet(it.position, it.setIndex) != null }
                        rpePicker = rpePicker?.takeIf { findSet(it.position, it.setIndex) != null }
                        loadRests(loaded)
                        ensureStopwatchTicker()
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
        names = loadExerciseNames(exerciseRepository, exerciseLibrary) ?: return
        traits = exerciseRepository.getAll().getOrNull()
            ?.associate { it.id to ExerciseTraits(it.metricType, it.equipment) } ?: traits
    }

    private fun metricOf(exerciseId: String): ExerciseMetricType =
        traits[exerciseId]?.metricType ?: ExerciseMetricType.WeightReps

    /**
     * The planned rest and the planned sets (weight and reps, shown as placeholders) of each exercise come from the
     * routine the session started from, matched by position and exercise; anything unmatched (or a failed read, or a
     * session without a routine) uses the default rest and the ghost placeholders (ADR-50).
     */
    private suspend fun loadRests(loaded: WorkoutSession) {
        val routine = loaded.routineId?.let { routineRepository.get(it).getOrNull() }
        rests = loaded.exercises.associate { exercise ->
            val planned = routine?.exercises?.firstOrNull {
                it.position == exercise.position && it.exerciseId == exercise.exerciseId
            }
            exercise.position to (planned?.restSeconds ?: RoutineBounds.DEFAULT_REST_SECONDS)
        }.toMutableMap()
        planned = buildMap {
            loaded.exercises.forEach { exercise ->
                val match = routine?.exercises?.firstOrNull {
                    it.position == exercise.position && it.exerciseId == exercise.exerciseId
                }
                match?.plannedSets?.forEachIndexed { index, set -> put(exercise.position to (index + 1), set) }
            }
        }
        targetRpes = buildMap {
            loaded.exercises.forEach { exercise ->
                routine?.exercises
                    ?.firstOrNull { it.position == exercise.position && it.exerciseId == exercise.exerciseId }
                    ?.targetRpe?.let { put(exercise.position, it) }
            }
        }
    }

    /** A failed read just means no ghost; it never blocks the session. */
    private suspend fun loadGhost(exerciseId: String) {
        val routineId = GhostData.routineFilter(previousMode, session?.routineId)
        repository.lastPerformance(exerciseId, routineId).getOrNull()?.takeIf { it.isNotEmpty() }
            ?.let { ghosts[exerciseId] = it }
    }

    private fun ghostFor(exerciseId: String, setIndex: Int): PreviousSet? =
        ghosts[exerciseId]?.let { GhostData.forSet(it, setIndex) }

    private fun usePrevious(position: Int, setIndex: Int) {
        val exercise = session?.exercises?.firstOrNull { it.position == position } ?: return
        val ghost = ghostFor(exercise.exerciseId, setIndex) ?: return
        val columns = SetColumns(
            ghost.weightKg,
            ghost.reps,
            ghost.durationS,
            ghost.distanceM,
            ghost.steps,
            ghost.floors,
        )
            .toEntry(metricOf(exercise.exerciseId)).columns()
        editSet(position, setIndex) {
            it.copy(
                weight = columns.weightKg?.let(::weightText).orEmpty(),
                reps = columns.reps?.toString().orEmpty(),
                distance = columns.distanceM?.let { metres -> weightText(metres / METRES_PER_KM) }.orEmpty(),
                steps = columns.steps?.toString().orEmpty(),
                floors = columns.floors?.toString().orEmpty(),
                stopwatch = columns.durationS?.let { seconds -> Stopwatch().withElapsedSeconds(seconds) }
                    ?: it.stopwatch,
                invalid = false,
            )
        }
    }

    /** Starts the set's stopwatch, or stops it keeping the time; the ticker republishes while one runs. */
    private fun toggleStopwatch(position: Int, setIndex: Int) {
        val now = clock.now()
        editSet(position, setIndex) {
            val next = if (it.stopwatch.isRunning) it.stopwatch.stop(now) else it.stopwatch.start(now)
            it.copy(stopwatch = next, invalid = false)
        }
        ensureStopwatchTicker()
    }

    private fun anyStopwatchRunning() =
        session?.exercises.orEmpty().any { e -> e.sets.any { it.completedAt == null && it.stopwatch.isRunning } }

    /** One coroutine republishes every second while a stopwatch runs, so the times keep counting on screen. */
    private fun ensureStopwatchTicker() {
        if (stopwatchJob?.isActive == true || !anyStopwatchRunning()) return
        stopwatchJob = viewModelScope.launch {
            while (anyStopwatchRunning()) {
                delay(TICK_MILLIS)
                publish()
            }
        }
    }

    private fun startRest(position: Int) {
        val duration = resolveRest(position, rests) ?: return
        restTimer = RestTimer.start(duration, clock.now())
        startRestTicker()
    }

    private fun startRestTicker() {
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
        if (event.checked) checkSet(event.position, event.setIndex) else uncheckSet(event.position, event.setIndex)
    }

    /** Validates, then completes the set; a working set without RPE first asks for it (ADR-55). */
    private fun checkSet(position: Int, setIndex: Int) {
        fillBlankFromSuggestions(position, setIndex)
        val filled = findSet(position, setIndex)
        val valid = filled?.let { parseEntry(it, metricOf(exerciseIdAt(position))) } != null
        val needsRpe = filled?.setType != SetType.WarmUp && filled?.rpe == null
        when {
            !valid -> {
                updateSet(position, setIndex) { it.copy(invalid = true) }
                publish()
            }

            needsRpe -> {
                updateSet(position, setIndex) { it.copy(invalid = false) }
                setRpePicker(RpePickerTarget(position, setIndex, completesSet = true))
            }

            else -> completeSet(position, setIndex)
        }
    }

    private fun uncheckSet(position: Int, setIndex: Int) {
        val now = clock.now()
        updateSet(position, setIndex) {
            it.copy(completedAt = null, invalid = false, stopwatch = it.stopwatch.stop(now))
        }
        write(position, setIndex)
    }

    /** Haptic, completion stamp, stopwatch stop, one write and the rest timer. */
    private fun completeSet(position: Int, setIndex: Int) {
        haptics.tick()
        val now = clock.now()
        updateSet(position, setIndex) {
            it.copy(completedAt = now, invalid = false, stopwatch = it.stopwatch.stop(now))
        }
        write(position, setIndex)
        // Marking a set "Listo" while paused resumes the session and its countdown (ADR-56).
        if (session?.pausedAt != null) resumeSession()
        // Offline the resume is refused, so the session stays paused and no countdown may run inside it.
        if (session?.pausedAt == null) startRest(position)
    }

    /** Untouched fields log the suggested (grey) values: planned first, then the last performance. */
    private fun fillBlankFromSuggestions(position: Int, setIndex: Int) {
        val exercise = session?.exercises?.firstOrNull { it.position == position } ?: return
        updateSet(position, setIndex) {
            it.copy(
                weight = it.weight.ifBlank { weightPlaceholderOf(exercise, it).orEmpty() },
                reps = it.reps.ifBlank { repsPlaceholderOf(exercise, it).orEmpty() },
            )
        }
    }

    private fun exerciseIdAt(position: Int): String =
        session?.exercises?.firstOrNull { it.position == position }?.exerciseId.orEmpty()

    private fun openTypePicker(position: Int, setIndex: Int) {
        if (terminal == null && findSet(position, setIndex) != null) setPicker(TypePickerTarget(position, setIndex))
    }

    private fun setPicker(target: TypePickerTarget?) {
        typePicker = target
        publish()
    }

    /** Applies the chosen type (also to a completed set) and queues the write like any other change of the set. */
    private fun chooseType(event: LiveSessionEvent.SetTypeChosen) {
        typePicker = null
        if (findSet(event.position, event.setIndex) == null) {
            publish()
            return
        }
        updateSet(event.position, event.setIndex) { it.copy(setType = event.type) }
        write(event.position, event.setIndex)
    }

    private fun openRpePicker(position: Int, setIndex: Int) {
        if (terminal == null && findSet(position, setIndex) != null) setRpePicker(RpePickerTarget(position, setIndex))
    }

    private fun setRpePicker(target: RpePickerTarget?) {
        rpePicker = target
        publish()
    }

    /** Applies the chosen RPE (also to a completed set) and queues the write; an off-grid value is ignored. */
    private fun chooseRpe(event: LiveSessionEvent.SetRpeChosen) {
        val completes = rpePicker?.completesSet == true
        rpePicker = null
        val set = findSet(event.position, event.setIndex)
        if (set == null || !RpeScale.isValid(event.rpe)) {
            publish()
            return
        }
        updateSet(event.position, event.setIndex) { it.copy(rpe = event.rpe) }
        if (completes &&
            set.completedAt == null
        ) {
            completeSet(event.position, event.setIndex)
        } else {
            write(event.position, event.setIndex)
        }
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
            if (exerciseId !in names || exerciseId !in traits) loadNames()
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
        if (!sessionActionsAllowed() || !hasCompletedSet()) return
        val pending = current.exercises.sumOf { exercise -> exercise.sets.count { it.completedAt == null } }
        updateDialogs { finishSheet = FinishSheet(pending) }
    }

    /** Finishing, pausing and resuming need the server, so they wait for a connection and for the idle screen. */
    private fun sessionActionsAllowed() = online && !busy && terminal == null

    private fun hasCompletedSet() = session?.exercises.orEmpty().any { e -> e.sets.any { it.completedAt != null } }

    /** Pauses locally at once (the rest countdown freezes too) and tells the server; refusing reverts it. */
    private fun pauseSession() {
        val current = session ?: return
        if (!sessionActionsAllowed() || current.pausedAt != null) return
        val at = clock.now()
        val running = restTimer
        session = current.copy(pausedAt = at)
        restTimer = running?.pause(at)
        tickerJob?.cancel()
        publish()
        viewModelScope.launch {
            if (repository.pause(current.id, at).isFailure) {
                val reverted = session?.pausedAt == at
                session = session?.let { if (it.pausedAt == at) it.copy(pausedAt = null) else it }
                if (reverted && running != null) {
                    restTimer = running
                    startRestTicker()
                }
                fail()
            }
        }
    }

    /**
     * Resumes locally at once (the paused span joins the total, the frozen countdown runs again) and tells the server;
     * refusing reverts both.
     */
    private fun resumeSession() {
        val current = session ?: return
        val pausedAt = current.pausedAt?.takeIf { sessionActionsAllowed() } ?: return
        val at = clock.now()
        val frozen = restTimer
        val resumed = current.copy(
            pausedAt = null,
            pausedTotalSeconds = current.pausedTotalSeconds + (at - pausedAt).inWholeSeconds.coerceAtLeast(0L),
        )
        session = resumed
        val resumedTimer = frozen?.resume(at)
        restTimer = resumedTimer
        if (restTimer != null) startRestTicker()
        publish()
        viewModelScope.launch {
            if (repository.resume(current.id, at).isFailure) {
                if (session == resumed) {
                    session = current
                    // A rest started after the resume (a set marked done) stays, frozen with the session.
                    restTimer = if (restTimer === resumedTimer) frozen else restTimer?.pause(clock.now())
                    tickerJob?.cancel()
                }
                fail()
            }
        }
    }

    /** A countdown frozen by a pause made before the screen was recreated comes back frozen (ADR-40). */
    private fun restoreFrozenRest() {
        val remaining = savedState.get<Long>(KEY_REST_PAUSED_REMAINING) ?: return
        if (session?.pausedAt == null || restTimer != null) return
        val total = savedState.get<Long>(KEY_REST_PAUSED_TOTAL) ?: remaining
        restTimer = RestTimer(clock.now(), maxOf(total, remaining).milliseconds, remaining.milliseconds)
    }

    /**
     * Removes the set at once and renumbers the later sets of the exercise (rows, drafts, stopwatches and pickers
     * travel with their set). The writes still queued are frozen into a [DeleteOp] with their current indexes so the
     * server receives them, then the deletion, in that order; writes made afterwards use the new indexes.
     */
    private fun deleteSet(position: Int, setIndex: Int) {
        val current = session ?: return
        if (busy || terminal != null || findSet(position, setIndex) == null) return
        replaceExercise(position) { exercise ->
            exercise.copy(
                sets = exercise.sets.filter { it.setIndex != setIndex }
                    .map { if (it.setIndex > setIndex) it.copy(setIndex = it.setIndex - 1) else it },
            )
        }
        typePicker = typePicker?.let { picker ->
            shiftedBy(picker.position to picker.setIndex, position, setIndex)
                ?.let { TypePickerTarget(it.first, it.second) }
        }
        rpePicker = rpePicker?.let { picker ->
            shiftedBy(picker.position to picker.setIndex, position, setIndex)
                ?.let { picker.copy(position = it.first, setIndex = it.second) }
        }
        val before = queue.drain().filterNot { it.exercisePosition == position && it.setIndex == setIndex }
        deleteOps.addLast(DeleteOp(current.id, position, setIndex, before.toMutableList()))
        unsynced = queuedKeys()
        publish()
        if (online) sync()
    }

    /** The greyed reps of a set: the routine's plan, else last time's value, else the one it was added with. */
    private fun repsPlaceholderOf(exercise: ExerciseState, set: SetState): String? =
        planned[exercise.position to set.setIndex]?.reps?.toString()
            ?: ghostFor(exercise.exerciseId, set.setIndex)?.reps?.toString()
            ?: set.repsPlaceholder

    /** The greyed weight of a set: the routine's plan, else last time's value, else the one it was added with. */
    private fun weightPlaceholderOf(exercise: ExerciseState, set: SetState): String? {
        val weighted = metricOf(exercise.exerciseId) == ExerciseMetricType.WeightReps
        return planned[exercise.position to set.setIndex]?.weightKg?.let { GhostData.weightText(it) }
            ?: ghostFor(exercise.exerciseId, set.setIndex)?.takeIf { weighted || it.weightKg != 0.0 }
                ?.let { GhostData.weightText(it.weightKg) }
            ?: set.weightPlaceholder
    }

    /**
     * Opens the calculator with the first working weight (typed, else the placeholder) and the bar (the handle for
     * dumbbells). The warm-up one starts from the best past weight of the exercise once it loads, as long as the user
     * has not typed their own.
     */
    private fun openCalculator(position: Int, kind: CalculatorKind) {
        val exercise = session?.exercises?.firstOrNull { it.position == position } ?: return
        if (terminal != null) return
        val reference = exercise.sets.firstOrNull { it.setType != SetType.WarmUp }
        val weight = reference?.let { it.weight.ifBlank { weightPlaceholderOf(exercise, it).orEmpty() } }.orEmpty()
        updateDialogs {
            calculatorDraft = CalculatorDraft(
                position,
                kind,
                weight,
                barFor(trainingPreferences.barWeightKg, traits[exercise.exerciseId]?.equipment, trainingPreferences),
            )
        }
        if (kind == CalculatorKind.WarmUp) applyBestPastWeight(position, exercise.exerciseId)
    }

    private fun applyBestPastWeight(position: Int, exerciseId: String) {
        viewModelScope.launch {
            val records = repository.personalRecords(exerciseId).getOrNull() ?: return@launch
            val best = BestPastWeight(records) ?: return@launch
            val draft = calculatorDraft
            val untouchedWarmUp = draft?.kind == CalculatorKind.WarmUp && !draft.weightEdited
            if (draft != null && untouchedWarmUp && draft.position == position) {
                calculatorDraft = draft.copy(weightText = weightText(best))
                publish()
            }
        }
    }

    private fun isDumbbell(position: Int?): Boolean {
        val exercise = session?.exercises?.firstOrNull { it.position == position } ?: return false
        return traits[exercise.exerciseId]?.equipment == Equipment.Dumbbell
    }

    /**
     * Inserts [sets] as warm-up sets after the exercise's leading warm-ups and before its first other set. The sets
     * from there on move up by the number inserted (rows, drafts, stopwatches and pickers travel with them), and every
     * moved or new set is queued under its new index with its full content: an upsert overwrites the row that held the
     * old index and the highest index is a new row, so the server ends up with exactly this order whatever order the
     * queue is flushed in. Writes still queued under an old index of a moved set are replaced by these.
     */
    private fun addWarmUpSets(position: Int, sets: List<WarmUpSet>) {
        val exercise = session?.exercises?.firstOrNull { it.position == position }
        if (exercise == null || sets.isEmpty() || busy) return
        if (terminal != null) return
        val insertAt = exercise.sets.indexOfFirst { it.setType != SetType.WarmUp }.takeIf { it >= 0 }
            ?: exercise.sets.size
        val added = sets.map {
            SetState(0, SetType.WarmUp, weightText(it.weightKg), it.reps.toString(), completedAt = null)
        }
        val merged = (exercise.sets.take(insertAt) + added + exercise.sets.drop(insertAt))
            .mapIndexed { index, set -> set.copy(setIndex = index + 1) }
        replaceExercise(position) { it.copy(sets = merged) }
        val firstMoved = insertAt + 1
        val moved = { pickerPosition: Int, pickerSet: Int -> pickerPosition == position && pickerSet >= firstMoved }
        typePicker =
            typePicker?.let { if (moved(it.position, it.setIndex)) it.copy(setIndex = it.setIndex + sets.size) else it }
        rpePicker =
            rpePicker?.let { if (moved(it.position, it.setIndex)) it.copy(setIndex = it.setIndex + sets.size) else it }
        calculatorDraft = null
        // Highest index first: the new row is created before the lower ones are overwritten.
        merged.drop(insertAt).reversed().forEach { write(position, it.setIndex) }
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
            val includeWarmUp = preferences.warmUpIncludedOrDefault()
            val summary = BuildSessionSummary(
                finished,
                endedAt,
                loadHistory(finished),
                includeWarmUp,
                bodyMeasurements.getLatestWeight().getOrNull()?.weightKg,
                exerciseOptions.doubleDumbbellIdsOrDefault(),
                loadRecordHistory(finished),
            )
            val named = current.exercises.associate { it.position to names[it.exerciseId] }
            busy = false
            finishSheet = null
            restTimer = null
            tickerJob?.cancel()
            terminal = LiveSessionUiState.Finished(LiveSummary(summary, named))
            publish()
            clearSavedState()
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
            clearSavedState()
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

    /** The record aggregates of every exercise with a completed set, or null when any cannot be read (no marks). */
    private suspend fun loadRecordHistory(finished: WorkoutSession): Map<String, List<ExerciseSessionRecords>>? {
        val ids = finished.exercises.filter { e -> e.sets.any { it.isCompleted } }.map { it.exerciseId }.distinct()
        val result = mutableMapOf<String, List<ExerciseSessionRecords>>()
        for (id in ids) {
            result[id] = repository.sessionRecords(id).getOrElse { return null }
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
                entry = entryOf(set, exercise.exerciseId),
                completedAt = set.completedAt,
                setType = set.setType,
                rpe = set.rpe,
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
        var allSent = flushDeletions()
        if (allSent) {
            for (write in queue.snapshot()) {
                if (!send(write)) {
                    allSent = false
                    break
                }
                queue.remove(write)
            }
        }
        unsynced = queuedKeys()
        allSent && queue.isEmpty && deleteOps.isEmpty()
    }

    private suspend fun send(write: SetWrite): Boolean = repository.upsertSet(
        write.sessionId,
        write.exerciseId,
        write.exercisePosition,
        write.supersetGroup,
        write.setIndex,
        write.entry,
        write.completedAt,
        write.setType,
        write.rpe,
    ).isSuccess

    /** Sends each pending deletion preceded by the writes frozen with it; false at the first failure. */
    private suspend fun flushDeletions(): Boolean {
        while (deleteOps.isNotEmpty()) {
            val op = deleteOps.first()
            var sent = true
            for (write in op.before.toList()) {
                if (!send(write)) {
                    sent = false
                    break
                }
                op.before.remove(write)
            }
            sent = sent && repository.deleteSet(op.sessionId, op.position, op.setIndex).isSuccess
            if (!sent) {
                if (!op.reported) {
                    op.reported = true
                    effectChannel.trySend(LiveSessionEffect.ActionFailed)
                }
                return false
            }
            deleteOps.removeFirst()
        }
        return true
    }

    /** Sets with a write not yet on the server, by their current index (frozen writes follow later deletions). */
    private fun queuedKeys(): Set<Pair<Int, Int>> {
        val keys = queue.snapshot().map { it.exercisePosition to it.setIndex }.toMutableSet()
        deleteOps.forEachIndexed { index, op ->
            op.before.forEach { write ->
                var key: Pair<Int, Int>? = write.exercisePosition to write.setIndex
                deleteOps.drop(index).forEach { later ->
                    key =
                        key?.let { shiftedBy(it, later.position, later.setIndex) }
                }
                key?.let(keys::add)
            }
        }
        return keys
    }

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
        persistState()
        _uiState.value = terminal ?: render()
    }

    private fun restoredPicker(): TypePickerTarget? {
        val position = savedState.get<Int>(KEY_PICKER_POSITION)
        val setIndex = savedState.get<Int>(KEY_PICKER_SET)
        return if (position != null && setIndex != null) TypePickerTarget(position, setIndex) else null
    }

    private fun restoredRpePicker(): RpePickerTarget? {
        val position = savedState.get<Int>(KEY_RPE_PICKER_POSITION)
        val setIndex = savedState.get<Int>(KEY_RPE_PICKER_SET)
        val completes = savedState.get<Boolean>(KEY_RPE_PICKER_COMPLETES) ?: false
        return if (position != null && setIndex != null) RpePickerTarget(position, setIndex, completes) else null
    }

    private fun restoredDoubleDumbbellDraft(): DoubleDumbbellDraft? {
        val exerciseId = savedState.get<String>(KEY_DOUBLE_DRAFT_EXERCISE) ?: return null
        return DoubleDumbbellDraft(exerciseId, savedState.get<Boolean>(KEY_DOUBLE_DRAFT_ENABLED) ?: false)
    }

    /** Mirrors the picker target and the unchecked weight/reps drafts into [savedState] (ADR-40). */
    private fun persistState() {
        savedState[KEY_DOUBLE_DRAFT_EXERCISE] = doubleDumbbellDraft?.exerciseId
        savedState[KEY_DOUBLE_DRAFT_ENABLED] = doubleDumbbellDraft?.enabled
        savedState[KEY_BODY_WEIGHT_DRAFT] = bodyWeightDraft
        savedState.persistCalculatorDraft(calculatorDraft)
        savedState[KEY_PICKER_POSITION] = typePicker?.position
        savedState[KEY_PICKER_SET] = typePicker?.setIndex
        savedState[KEY_RPE_PICKER_POSITION] = rpePicker?.position
        savedState[KEY_RPE_PICKER_SET] = rpePicker?.setIndex
        savedState[KEY_RPE_PICKER_COMPLETES] = rpePicker?.completesSet
        session?.takeIf { terminal == null }?.let {
            savedState[KEY_PAUSE_KNOWN] = true
            savedState[KEY_PAUSED_AT] = it.pausedAt?.toString()
            savedState[KEY_PAUSED_TOTAL] = it.pausedTotalSeconds
            val frozen = restTimer?.takeIf { timer -> timer.isPaused }
            savedState[KEY_REST_PAUSED_REMAINING] = frozen?.pausedRemaining?.inWholeMilliseconds
            savedState[KEY_REST_PAUSED_TOTAL] = frozen?.total?.inWholeMilliseconds
        }
        if (terminal != null) return
        val drafts = session?.exercises.orEmpty().flatMap { exercise ->
            exercise.sets.filter { it.completedAt == null && it.hasDraft }.map {
                listOf(
                    exercise.position,
                    it.setIndex,
                    it.weight,
                    it.reps,
                    it.distance,
                    it.steps,
                    it.floors,
                    it.stopwatch.elapsedBeforeMs,
                    it.stopwatch.startedAtMs ?: "",
                ).joinToString("$DRAFT_SEPARATOR")
            }
        }
        savedState[KEY_DRAFTS] = ArrayList(drafts)
    }

    /** The session is over: its picker target and drafts must not leak into the next one. */
    private fun clearSavedState() {
        typePicker = null
        rpePicker = null
        doubleDumbbellDraft = null
        bodyWeightDraft = null
        calculatorDraft = null
        savedState.persistCalculatorDraft(null)
        savedState.remove<String>(KEY_DOUBLE_DRAFT_EXERCISE)
        savedState.remove<Boolean>(KEY_DOUBLE_DRAFT_ENABLED)
        savedState.remove<String>(KEY_BODY_WEIGHT_DRAFT)
        savedState.remove<Int>(KEY_PICKER_POSITION)
        savedState.remove<Int>(KEY_PICKER_SET)
        savedState.remove<Int>(KEY_RPE_PICKER_POSITION)
        savedState.remove<Int>(KEY_RPE_PICKER_SET)
        savedState.remove<Boolean>(KEY_RPE_PICKER_COMPLETES)
        savedState.remove<List<String>>(KEY_DRAFTS)
        savedState.remove<Boolean>(KEY_PAUSE_KNOWN)
        savedState.remove<String>(KEY_PAUSED_AT)
        savedState.remove<Long>(KEY_PAUSED_TOTAL)
        savedState.remove<Long>(KEY_REST_PAUSED_REMAINING)
        savedState.remove<Long>(KEY_REST_PAUSED_TOTAL)
    }

    /** A pause made before the screen was recreated wins over the loaded row (the server call may be in flight). */
    private fun SessionState.withRestoredPause(): SessionState {
        if (savedState.get<Boolean>(KEY_PAUSE_KNOWN) != true) return this
        return copy(
            pausedAt = savedState.get<String>(KEY_PAUSED_AT)?.let(Instant::parse),
            pausedTotalSeconds = savedState.get<Long>(KEY_PAUSED_TOTAL) ?: pausedTotalSeconds,
        )
    }

    /** Puts back the drafts typed before process death over the reloaded server state (unchecked sets only). */
    private fun SessionState.withRestoredDrafts(): SessionState {
        val drafts = savedState.get<List<String>>(KEY_DRAFTS).orEmpty().mapNotNull { row ->
            val fields = row.split(DRAFT_SEPARATOR).takeIf { it.size == DRAFT_FIELDS }
            val position = fields?.get(0)?.toIntOrNull()
            val setIndex = fields?.get(1)?.toIntOrNull()
            if (fields == null || position == null || setIndex == null) null else (position to setIndex) to fields
        }.toMap()
        if (drafts.isEmpty()) return this
        return copy(
            exercises = exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        val draft = drafts[exercise.position to set.setIndex]
                        if (draft == null || set.completedAt != null) set else set.withDraft(draft)
                    },
                )
            },
        )
    }

    /** The set with the typed values of a saved draft row (see [persistState] for the field order). */
    @Suppress("MagicNumber") // Indexes of the saved draft row.
    private fun SetState.withDraft(fields: List<String>) = copy(
        weight = fields[2],
        reps = fields[3],
        distance = fields[4],
        steps = fields[5],
        floors = fields[6],
        stopwatch = Stopwatch(fields[7].toLongOrNull() ?: 0L, fields[8].toLongOrNull()),
    )

    @Suppress("LongMethod") // One flat mapping of the session to the screen state.
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
                    metricType = metricOf(exercise.exerciseId),
                    equipment = traits[exercise.exerciseId]?.equipment,
                    sets = exercise.sets.map { set ->
                        val ghost = ghostFor(exercise.exerciseId, set.setIndex)
                        val now = clock.now()
                        val weighted = metricOf(exercise.exerciseId) == ExerciseMetricType.WeightReps
                        LiveSetRow(
                            setIndex = set.setIndex,
                            weight = set.weight,
                            reps = set.reps,
                            completed = set.completedAt != null,
                            distance = set.distance,
                            steps = set.steps,
                            floors = set.floors,
                            durationSeconds = set.stopwatch.elapsedSeconds(now),
                            stopwatchRunning = set.stopwatch.isRunning,
                            weightPlaceholder = weightPlaceholderOf(exercise, set),
                            repsPlaceholder = repsPlaceholderOf(exercise, set),
                            type = set.setType,
                            previous = GhostData.previousText(ghost, metricOf(exercise.exerciseId)),
                            pendingSave = (exercise.position to set.setIndex) in unsynced,
                            invalid = set.invalid,
                            rpe = set.rpe?.let(RpeScale::text),
                        )
                    },
                    restSeconds = rests[exercise.position] ?: RoutineBounds.DEFAULT_REST_SECONDS,
                    targetRpeText = targetRpes[exercise.position]?.let(RpeScale::text),
                )
            },
            restTimer = restTimer?.let { RestTimerUi(it.remaining(clock.now()), it.total, it.isPaused) },
            isOnline = online,
            finishSheet = finishSheet,
            discardConfirm = discardAsked,
            busy = busy,
            typePicker = typePicker,
            rpePicker = rpePicker,
            doubleDumbbellIds = doubleDumbbellIds,
            bodyWeightKg = bodyWeightKg,
            todayHasHealthConnectWeight = todayHasHealthConnectWeight,
            doubleDumbbellDraft = doubleDumbbellDraft,
            bodyWeightDraft = bodyWeightDraft,
            bodyWeightInvalid = bodyWeightInvalid,
            pausedAt = current.pausedAt,
            pausedTotalSeconds = current.pausedTotalSeconds,
            calculator = calculatorDraft?.let { draft ->
                val exercise = current.exercises.firstOrNull { it.position == draft.position }
                exercise?.let {
                    calculatorUiState(
                        draft,
                        trainingPreferences,
                        traits[it.exerciseId]?.equipment,
                    )
                }
            },
        )
    }

    private fun WorkoutSession.toState() = SessionState(
        id = id,
        routineId = routineId,
        name = name,
        startedAt = startedAt,
        pausedAt = pausedAt,
        pausedTotalSeconds = pausedTotalSeconds,
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
        val columns = entry.columns()
        val done = completedAt != null
        fun Double?.text(scale: Double = 1.0) =
            this?.takeIf { done || it != 0.0 }?.let { weightText(it / scale) }.orEmpty()
        fun Int?.text() = this?.takeIf { done || it != 0 }?.toString().orEmpty()
        return SetState(
            setIndex = setIndex,
            setType = setType,
            weight = columns.weightKg.text(),
            reps = columns.reps.text(),
            completedAt = completedAt,
            rpe = rpe?.let(RpeScale::round),
            distance = columns.distanceM.text(METRES_PER_KM),
            steps = columns.steps.text(),
            floors = columns.floors.text(),
            stopwatch = columns.durationS?.takeIf { done || it != 0 }
                ?.let { Stopwatch().withElapsedSeconds(it) } ?: Stopwatch(),
        )
    }

    private fun SessionState.toWorkoutSession(endedAt: Instant) = WorkoutSession(
        id = id,
        routineId = routineId,
        name = name,
        status = SessionStatus.Completed,
        startedAt = startedAt,
        endedAt = endedAt,
        pausedAt = pausedAt,
        pausedTotalSeconds = pausedTotalSeconds,
        exercises = exercises.map { exercise ->
            SessionExercise(
                exercise.exerciseId,
                exercise.position,
                exercise.supersetGroup,
                exercise.sets.map { set ->
                    WorkoutSet(
                        set.setIndex,
                        set.setType,
                        entryOf(set, exercise.exerciseId),
                        set.completedAt,
                        set.rpe,
                    )
                },
            )
        },
    )

    /** The set's draft as an entry, or an all-zero one of its metric type while it is not valid. */
    private fun entryOf(set: SetState, exerciseId: String): SetEntry =
        parseEntry(set, metricOf(exerciseId)) ?: SetEntry.empty(metricOf(exerciseId))

    /**
     * The set's draft as an entry of [metric]: reps from 1 to 999, a weight of zero or more (optional extra or
     * assistance load for bodyweight, weighted and assisted), a positive time, distance, steps or floors as the
     * metric asks; null when it is not valid.
     */
    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the metric types.
    private fun parseEntry(set: SetState, metric: ExerciseMetricType): SetEntry? {
        val weight = NumberInputDefaults.parse(set.weight)?.takeIf { it >= 0.0 }
        val optionalWeight = if (set.weight.isBlank()) 0.0 else weight
        val reps = set.reps.trim().toIntOrNull()?.takeIf { it in 1..MAX_REPS }
        val seconds = set.stopwatch.elapsedSeconds(clock.now()).takeIf { it > 0 }
        val metres = NumberInputDefaults.parse(set.distance)?.takeIf { it > 0.0 }?.let { it * METRES_PER_KM }
        val steps = set.steps.trim().toIntOrNull()?.takeIf { it > 0 }
        val floors = set.floors.trim().toIntOrNull()?.takeIf { it > 0 }
        return when (metric) {
            ExerciseMetricType.WeightReps -> weight?.let { w -> reps?.let { SetEntry.WeightReps(w, it) } }

            ExerciseMetricType.RepsOnly -> reps?.let { SetEntry.RepsOnly(it) }

            ExerciseMetricType.Duration -> seconds?.let { SetEntry.Duration(it) }

            ExerciseMetricType.WeightDuration -> weight?.let { w -> seconds?.let { SetEntry.WeightDuration(w, it) } }

            ExerciseMetricType.DistanceDuration ->
                metres?.let { m -> seconds?.let { SetEntry.DistanceDuration(m, it) } }

            ExerciseMetricType.Bodyweight -> optionalWeight?.let { w -> reps?.let { SetEntry.Bodyweight(w, it) } }

            ExerciseMetricType.Assisted -> optionalWeight?.let { w -> reps?.let { SetEntry.Assisted(w, it) } }

            ExerciseMetricType.Weighted -> optionalWeight?.let { w -> reps?.let { SetEntry.Weighted(w, it) } }

            ExerciseMetricType.StepsDuration -> steps?.let { n -> seconds?.let { SetEntry.StepsDuration(n, it) } }

            ExerciseMetricType.FloorsDuration -> floors?.let { n -> seconds?.let { SetEntry.FloorsDuration(n, it) } }
        }
    }
}
