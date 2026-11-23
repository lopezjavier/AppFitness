package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.repository.TrainingStatsRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildMuscleSetsHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sets per muscle group for each of the last 8 ISO weeks (US-145, planning/07 section 3 D6), all groups or one. */
class MuscleVolumeViewModel(
    private val stats: TrainingStatsRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private var selected: MuscleGroup? = MuscleGroup.entries.firstOrNull {
        it.name == savedState.get<String>(KEY_SELECTED)
    }

    private val _uiState = MutableStateFlow<MuscleVolumeUiState>(MuscleVolumeUiState.Loading)
    val uiState: StateFlow<MuscleVolumeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: MuscleVolumeEvent) {
        when (event) {
            MuscleVolumeEvent.Retry -> load()

            is MuscleVolumeEvent.SelectGroup -> {
                val content = _uiState.value as? MuscleVolumeUiState.Content ?: return
                if (event.group != null && event.group !in content.history.series) return
                selected = event.group
                saveSelection()
                _uiState.value = content.copy(selected = selected)
            }
        }
    }

    private fun load() {
        _uiState.value = MuscleVolumeUiState.Loading
        viewModelScope.launch {
            val weeks = stats.weeklyMuscleSets(WEEKS).getOrNull()
            if (weeks == null) {
                _uiState.value = MuscleVolumeUiState.Error
                return@launch
            }
            val history = BuildMuscleSetsHistory(weeks)
            if (history.series.isEmpty()) {
                _uiState.value = MuscleVolumeUiState.Empty
            } else {
                if (selected != null && selected !in history.series) {
                    selected = null
                    saveSelection()
                }
                _uiState.value = MuscleVolumeUiState.Content(history, selected)
            }
        }
    }

    private fun saveSelection() {
        selected?.let { savedState[KEY_SELECTED] = it.name } ?: savedState.remove<String>(KEY_SELECTED)
    }

    private companion object {
        const val KEY_SELECTED = "selected_group"
        const val WEEKS = 8
    }
}
