package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseMetricType
import io.github.lopezjavier.appfitness.domain.model.ExerciseSessionRecords
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressMetric
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.TrainingPreferencesRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.repository.warmUpIncludedOrDefault
import io.github.lopezjavier.appfitness.domain.usecase.BuildExerciseProgress
import io.github.lopezjavier.appfitness.domain.usecase.ChartMetricSelection
import io.github.lopezjavier.appfitness.feature.workout.loadExerciseLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Progress of one exercise (US-15): 1RM and volume per session, or max reps when it was never logged with load.
 * The whole history is read once; the range filter is applied on the client, so changing it is instant.
 */
@Suppress("LongParameterList") // Collaborators are injected.
class ExerciseProgressViewModel(
    private val exerciseId: String,
    private val workouts: WorkoutSessionRepository,
    private val strength: StrengthRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    private val clock: Clock,
    private val preferences: TrainingPreferencesRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var name: String? = null
    private var records: List<ExerciseSessionRecords> = emptyList()
    private var metricType = ExerciseMetricType.WeightReps
    private var maxReps: List<MaxRepsPoint> = emptyList()
    private var range = ProgressRange.entries.firstOrNull { it.name == savedState.get<String>(KEY_RANGE) }
        ?: ProgressRange.ThreeMonths

    private val _uiState = MutableStateFlow<ExerciseProgressUiState>(ExerciseProgressUiState.Loading)
    val uiState: StateFlow<ExerciseProgressUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: ExerciseProgressEvent) {
        when (event) {
            ExerciseProgressEvent.Retry -> if (_uiState.value == ExerciseProgressUiState.Error) load()

            is ExerciseProgressEvent.SelectRange -> {
                range = event.range
                savedState[KEY_RANGE] = range.name
                if (_uiState.value is ExerciseProgressUiState.Content) publish()
            }
        }
    }

    private fun load() {
        _uiState.value = ExerciseProgressUiState.Loading
        viewModelScope.launch {
            val sessions = workouts.sessionRecords(exerciseId).getOrNull()
            val lookup = loadExerciseLookup(exerciseRepository, exerciseLibrary)
            if (sessions == null || lookup == null) {
                _uiState.value = ExerciseProgressUiState.Error
                return@launch
            }
            val type = lookup.metricTypes[exerciseId] ?: ExerciseMetricType.WeightReps
            // Repetitions are only needed when a load exercise never had a 1RM (logged without load).
            val needsReps = ChartMetricSelection.metric(type) == ProgressMetric.OneRepMax &&
                sessions.isNotEmpty() && sessions.none { it.bestOneRm != null }
            val reps = if (needsReps) {
                strength.maxRepsHistory(exerciseId, includeWarmUp = preferences.warmUpIncludedOrDefault()).getOrNull()
            } else {
                emptyList()
            }
            if (reps == null) {
                _uiState.value = ExerciseProgressUiState.Error
                return@launch
            }
            records = sessions
            metricType = type
            maxReps = reps
            name = lookup.names[exerciseId]
            if (sessions.isEmpty()) _uiState.value = ExerciseProgressUiState.Empty(name) else publish()
        }
    }

    private fun publish() {
        _uiState.value = ExerciseProgressUiState.Content(
            name = name,
            range = range,
            progress = BuildExerciseProgress(records, metricType, maxReps, range, clock.today()),
        )
    }

    private companion object {
        const val KEY_RANGE = "range"
    }
}
