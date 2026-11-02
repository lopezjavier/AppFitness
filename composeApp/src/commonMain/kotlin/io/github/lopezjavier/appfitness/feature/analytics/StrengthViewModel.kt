package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.ExerciseNames
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.repository.WorkoutSessionRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildStrengthSummary
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.RankTopExercises
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
    exerciseRepository: ExerciseRepository,
    exerciseLibrary: ExerciseLibrary,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val exerciseNames: ExerciseNameResolver = ExerciseNameResolver(exerciseRepository, exerciseLibrary),
) : ViewModel() {
    private var chips: List<StrengthChip> = emptyList()
    private var selectedId: String? = savedState.get<String>(KEY_SELECTED)
    private val summaries = mutableMapOf<String, StrengthSummary>()

    private val _uiState = MutableStateFlow<StrengthUiState>(StrengthUiState.Loading)
    val uiState: StateFlow<StrengthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { exerciseNames.observeChanges().collect(::renameChips) }
        load()
    }

    fun onEvent(event: StrengthEvent) {
        when (event) {
            StrengthEvent.Retry -> if (_uiState.value == StrengthUiState.Error) load()

            is StrengthEvent.SelectExercise -> if (chips.any { it.exerciseId == event.exerciseId }) {
                selectedId = event.exerciseId
                savedState[KEY_SELECTED] = event.exerciseId
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
            val names = exerciseNames.resolve().getOrNull()
            val named = names?.let { nameChips(top, it) }
            if (named == null) {
                // A read failure or an id without a name is a failed load, never a chip with a generic label.
                _uiState.value = StrengthUiState.Error
                return@launch
            }
            chips = named
            val keep = selectedId?.takeIf { id -> top.contains(id) } ?: top.first()
            selectedId = keep
            savedState[KEY_SELECTED] = keep
            loadSummary(keep)
        }
    }

    private fun nameChips(ids: List<String>, names: ExerciseNames): List<StrengthChip>? =
        ids.map { id -> StrengthChip(id, names[id] ?: return null) }

    /** A renamed or new custom exercise updates the chips already shown; a partial lookup changes nothing. */
    private fun renameChips(names: ExerciseNames) {
        if (chips.isEmpty()) return
        chips = nameChips(chips.map { it.exerciseId }, names) ?: return
        if (_uiState.value is StrengthUiState.Content) publish()
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

    private companion object {
        const val KEY_SELECTED = "selected_exercise"
    }
}
