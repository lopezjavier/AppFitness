package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildStrengthSummary
import io.github.lopezjavier.appfitness.domain.usecase.RankTopExercises
import io.github.lopezjavier.appfitness.feature.workout.loadExerciseNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days

private const val WINDOW_DAYS = 42

/**
 * "Fuerza y 1RM estimado" card (US-15): the 3 most trained exercises of the last 6 weeks, the 1RM record of the
 * selected one and its change over the last month.
 */
@Suppress("LongParameterList") // Collaborators are injected.
class StrengthViewModel(
    private val strength: StrengthRepository,
    private val workouts: WorkoutSessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val exerciseLibrary: ExerciseLibrary,
    private val clock: Clock,
) : ViewModel() {
    private var chips: List<StrengthChip> = emptyList()
    private var selectedId: String? = null
    private val summaries = mutableMapOf<String, StrengthSummary>()

    private val _uiState = MutableStateFlow<StrengthUiState>(StrengthUiState.Loading)
    val uiState: StateFlow<StrengthUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: StrengthEvent) {
        when (event) {
            StrengthEvent.Retry -> if (_uiState.value == StrengthUiState.Error) load()

            is StrengthEvent.SelectExercise -> if (chips.any { it.exerciseId == event.exerciseId }) {
                selectedId = event.exerciseId
                if (event.exerciseId in summaries) publish() else loadSummary(event.exerciseId)
            }
        }
    }

    private fun load() {
        _uiState.value = StrengthUiState.Loading
        viewModelScope.launch {
            val sessions = strength.recentSessions(clock.now() - WINDOW_DAYS.days).getOrNull()
            if (sessions == null) {
                _uiState.value = StrengthUiState.Error
                return@launch
            }
            val top = RankTopExercises(sessions, clock.now() - WINDOW_DAYS.days)
            if (top.isEmpty()) {
                _uiState.value = StrengthUiState.Empty
                return@launch
            }
            val names = loadExerciseNames(exerciseRepository, exerciseLibrary).orEmpty()
            chips = top.map { StrengthChip(it, names[it]) }
            val keep = selectedId?.takeIf { id -> top.contains(id) } ?: top.first()
            selectedId = keep
            loadSummary(keep)
        }
    }

    private fun loadSummary(exerciseId: String) {
        publish()
        viewModelScope.launch {
            workouts.exerciseHistory(exerciseId).fold(
                onSuccess = { history ->
                    summaries[exerciseId] = BuildStrengthSummary(history, clock.today())
                    publish()
                },
                onFailure = { _uiState.value = StrengthUiState.Error },
            )
        }
    }

    private fun publish() {
        val id = selectedId ?: return
        _uiState.value = StrengthUiState.Content(chips, id, summaries[id])
    }
}
