package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseSetCount
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.model.SetCountPoint
import io.github.lopezjavier.appfitness.domain.model.TrainingSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingStatsRepository
import io.github.lopezjavier.appfitness.domain.usecase.SetCountBuckets
import io.github.lopezjavier.appfitness.domain.usecase.StatsWindow
import io.github.lopezjavier.appfitness.feature.workout.ExerciseLookup
import io.github.lopezjavier.appfitness.feature.workout.loadExerciseLookup
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "Estadisticas" screen (US-40, US-41): key values, set-count chart, sets per muscle group, top exercises and radar
 * for the selected frame, plus the 7-day body map and the detail of the open muscle. Every block loads on its own, so
 * one failure never blanks the rest. The frame and the open muscle survive recreation through [savedState].
 */
@Suppress("TooManyFunctions") // One loader per section.
class TrainingStatsViewModel(
    private val stats: TrainingStatsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val initialRange = ProgressRange.entries.firstOrNull { it.name == savedState.get<String>(KEY_RANGE) }
        ?: DEFAULT_RANGE
    private val initialMuscle = MuscleGroup.entries.firstOrNull { it.name == savedState.get<String>(KEY_MUSCLE) }

    private val _uiState = MutableStateFlow(TrainingStatsUiState(range = initialRange, openMuscle = initialMuscle))
    val uiState: StateFlow<TrainingStatsUiState> = _uiState.asStateFlow()

    private var frameJob: Job? = null
    private var bodyJob: Job? = null
    private var detailJob: Job? = null
    private var lookupLoad: Deferred<ExerciseLookup?>? = null

    init {
        loadFrame()
        loadBody()
        initialMuscle?.let(::loadDetail)
    }

    fun onEvent(event: TrainingStatsEvent) {
        when (event) {
            is TrainingStatsEvent.SelectRange -> {
                if (event.range == _uiState.value.range && frameJob?.isActive != true) return
                savedState[KEY_RANGE] = event.range.name
                _uiState.update { it.copy(range = event.range) }
                loadFrame()
            }

            is TrainingStatsEvent.Retry -> retry(event.section)

            is TrainingStatsEvent.OpenMuscle -> {
                savedState[KEY_MUSCLE] = event.group.name
                _uiState.update { it.copy(openMuscle = event.group) }
                loadDetail(event.group)
            }

            TrainingStatsEvent.CloseMuscle -> {
                savedState.remove<String>(KEY_MUSCLE)
                detailJob?.cancel()
                _uiState.update { it.copy(openMuscle = null, muscleDetail = SectionState.Loading) }
            }
        }
    }

    private fun retry(section: StatsSection?) {
        when (section) {
            null -> {
                loadFrame()
                loadBody()
                _uiState.value.openMuscle?.let(::loadDetail)
            }

            StatsSection.Body -> loadBody()

            StatsSection.Sheet -> _uiState.value.openMuscle?.let(::loadDetail)

            else -> loadFrame()
        }
    }

    // The RPCs group by UTC date, so the windows are computed on the UTC date too.
    private fun todayUtc(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    private fun loadFrame() {
        frameJob?.cancel()
        _uiState.update {
            it.copy(
                summary = SectionState.Loading,
                setCounts = SectionState.Loading,
                muscleSets = SectionState.Loading,
                topExercises = SectionState.Loading,
                radar = SectionState.Loading,
            )
        }
        val range = _uiState.value.range
        frameJob = viewModelScope.launch {
            val today = todayUtc()
            val window = StatsWindow.forRange(range, today)
            val previous = window.previous()
            val summary = async { stats.summary(window.from, window.to) }
            val weekly = async { stats.weeklySetCounts(window.from, window.to) }
            val current = async { stats.muscleGroupSets(window.from, window.to) }
            val before = previous?.let { async { stats.muscleGroupSets(it.from, it.to) } }
            val exercises = async { stats.exerciseSetCounts(window.from, window.to) }
            val lookup = async { exerciseLookup() }

            launch { publishSummary(summary.await()) }
            launch { publishSetCounts(range, weekly.await(), window, today) }
            launch { publishMuscles(current.await()) }
            launch { publishRadar(current.await(), before) }
            launch { publishTopExercises(exercises.await(), lookup.await()) }
        }
    }

    private fun publishSummary(result: Result<TrainingSummary>) {
        val value = result.getOrNull()
        val state = when {
            value == null -> SectionState.Error
            value.workouts == 0 -> SectionState.Empty
            else -> SectionState.Content(value)
        }
        _uiState.update { it.copy(summary = state) }
    }

    private fun publishSetCounts(
        range: ProgressRange,
        result: Result<List<SetCountPoint>>,
        window: StatsWindow,
        today: LocalDate,
    ) {
        val points = result.getOrNull()
        val state = if (points == null) {
            SectionState.Error
        } else {
            val buckets = SetCountBuckets.bucketsFor(range, points, window, today)
            if (buckets.sumOf { it.sets } == 0) SectionState.Empty else SectionState.Content(buckets)
        }
        _uiState.update { it.copy(setCounts = state) }
    }

    private fun publishMuscles(result: Result<List<MuscleGroupSets>>) {
        val rows = result.getOrNull()
        _uiState.update {
            it.copy(
                muscleSets =
                rows?.let { r -> SectionState.Content(allGroups(r)) } ?: SectionState.Error,
            )
        }
    }

    /** Loaded once per ViewModel and shared by the frame and the muscle sheet; a failed load is retried next time. */
    private suspend fun exerciseLookup(): ExerciseLookup? {
        val pending = lookupLoad
            ?: viewModelScope.async { loadExerciseLookup(exerciseRepository, exerciseLibrary) }.also { lookupLoad = it }
        val result = pending.await()
        if (result == null && lookupLoad === pending) lookupLoad = null
        return result
    }

    private suspend fun publishRadar(
        current: Result<List<MuscleGroupSets>>,
        before: Deferred<Result<List<MuscleGroupSets>>>?,
    ) {
        val now = current.getOrNull()
        val previousResult = before?.await()
        val state = if (now == null) {
            SectionState.Error
        } else {
            SectionState.Content(
                MuscleRadar(
                    current = allGroups(now),
                    previous = previousResult?.getOrNull()?.let(::allGroups),
                    previousFailed = previousResult?.isFailure == true,
                ),
            )
        }
        _uiState.update { it.copy(radar = state) }
    }

    private fun publishTopExercises(result: Result<List<ExerciseSetCount>>, lookup: ExerciseLookup?) {
        val rows = result.getOrNull()
        val state = when {
            rows == null || lookup == null -> SectionState.Error

            rows.isEmpty() -> SectionState.Empty

            else -> SectionState.Content(
                rows.sortedByDescending {
                    it.sets
                }.take(TOP_EXERCISES).map { it.named(lookup) },
            )
        }
        _uiState.update { it.copy(topExercises = state) }
    }

    private fun loadBody() {
        bodyJob?.cancel()
        _uiState.update { it.copy(bodyMap = SectionState.Loading) }
        bodyJob = viewModelScope.launch {
            val window = StatsWindow.lastSevenDays(todayUtc())
            val rows = stats.muscleGroupSets(window.from, window.to).getOrNull()
            val state = rows?.let { r ->
                SectionState.Content(allGroups(r).filter { it.muscleGroup != MuscleGroup.FullBody })
            } ?: SectionState.Error
            _uiState.update { it.copy(bodyMap = state) }
        }
    }

    private fun loadDetail(group: MuscleGroup) {
        detailJob?.cancel()
        _uiState.update { it.copy(muscleDetail = SectionState.Loading) }
        detailJob = viewModelScope.launch {
            val window = StatsWindow.lastSevenDays(todayUtc())
            val sets = async { stats.muscleGroupSets(window.from, window.to) }
            val exercises = async { stats.exerciseSetCounts(window.from, window.to, group) }
            val lookup = async { exerciseLookup() }
            val totals = sets.await().getOrNull()
            val rows = exercises.await().getOrNull()
            val names = lookup.await()
            val state = if (totals == null || rows == null || names == null) {
                SectionState.Error
            } else {
                SectionState.Content(
                    MuscleDetail(
                        group = group,
                        sets = totals.firstOrNull { it.muscleGroup == group }?.sets ?: 0.0,
                        exercises = rows.sortedByDescending { it.sets }.map { it.named(names) },
                    ),
                )
            }
            if (_uiState.value.openMuscle == group) _uiState.update { it.copy(muscleDetail = state) }
        }
    }

    private fun ExerciseSetCount.named(lookup: ExerciseLookup) =
        StatsExercise(exerciseId, lookup.names[exerciseId], sets, sessions)

    /** One entry per muscle group in catalog order, zeros included, so the shapes are stable. */
    private fun allGroups(rows: List<MuscleGroupSets>): List<MuscleGroupSets> = MuscleGroup.entries.map { group ->
        MuscleGroupSets(group, rows.filter { it.muscleGroup == group }.sumOf { it.sets })
    }

    companion object {
        val DEFAULT_RANGE = ProgressRange.OneMonth
        private const val KEY_RANGE = "range"
        private const val KEY_MUSCLE = "muscle"
        private const val TOP_EXERCISES = 5
    }
}
