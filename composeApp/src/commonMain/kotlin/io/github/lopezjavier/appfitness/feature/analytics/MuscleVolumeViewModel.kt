package io.github.lopezjavier.appfitness.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.StrengthRepository
import io.github.lopezjavier.appfitness.domain.usecase.BuildMuscleVolumeHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Weekly volume per muscle group from the first session, up to 8 ISO weeks (US-15), all groups or one of them. */
class MuscleVolumeViewModel(
    private val strength: StrengthRepository,
    private val clock: Clock,
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
            // The RPC groups weeks by UTC date, so the window is computed on the UTC date too.
            val today = clock.now().toLocalDateTime(TimeZone.UTC).date
            val rows = strength.weeklyMuscleVolume(BuildMuscleVolumeHistory.FIRST_QUERY_DATE).getOrNull()
            if (rows == null) {
                _uiState.value = MuscleVolumeUiState.Error
                return@launch
            }
            val history = BuildMuscleVolumeHistory(rows, today)
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
    }
}
