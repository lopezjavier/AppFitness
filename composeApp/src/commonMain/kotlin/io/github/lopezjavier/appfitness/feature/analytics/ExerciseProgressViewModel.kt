package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseHistoryPoint
import io.github.lopezjavier.appfitness.domain.model.MaxRepsPoint
import io.github.lopezjavier.appfitness.domain.model.ProgressRange
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildExerciseProgress
import io.github.lopezjavier.appfitness.feature.workout.loadExerciseNames
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
) : ViewModel() {
    private var name: String? = null
    private var history: List<ExerciseHistoryPoint> = emptyList()
    private var maxReps: List<MaxRepsPoint> = emptyList()
    private var range = ProgressRange.ThreeMonths

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
                if (_uiState.value is ExerciseProgressUiState.Content) publish()
            }
        }
    }

    private fun load() {
        _uiState.value = ExerciseProgressUiState.Loading
        viewModelScope.launch {
            val points = workouts.exerciseHistory(exerciseId).getOrNull()
            // Repetitions are only needed when no session has a 1RM (exercise logged without load).
            val reps = when {
                points == null -> null
                points.any { it.bestOneRepMax != null } -> emptyList()
                points.isEmpty() -> emptyList()
                else -> strength.maxRepsHistory(exerciseId).getOrNull()
            }
            if (points == null || reps == null) {
                _uiState.value = ExerciseProgressUiState.Error
                return@launch
            }
            history = points
            maxReps = reps
            name = loadExerciseNames(exerciseRepository, exerciseLibrary)?.get(exerciseId)
            if (points.isEmpty()) _uiState.value = ExerciseProgressUiState.Empty else publish()
        }
    }

    private fun publish() {
        _uiState.value = ExerciseProgressUiState.Content(
            name = name,
            range = range,
            progress = BuildExerciseProgress(history, maxReps, range, clock.today()),
        )
    }
}
