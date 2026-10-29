package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Trainer tab root (US-68): the derived values with their confirmation state. The open edit sheet and its picked
 * level survive recreation through [savedState]; the loaded values are always re-fetched, never persisted.
 */
class TrainerViewModel(
    private val repository: CoachRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<TrainerUiState>(TrainerUiState.Loading)
    val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<TrainerEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    fun onEvent(event: TrainerEvent) {
        when (event) {
            TrainerEvent.Load, TrainerEvent.Retry -> load()
            TrainerEvent.Confirm -> submit(level = null, fromSheet = false)
            TrainerEvent.OpenEdit -> openEdit()
            is TrainerEvent.PickLevel -> pickLevel(event.level)
            TrainerEvent.CloseEdit -> setDraft(null)
        }
    }

    private fun load() {
        _uiState.value = TrainerUiState.Loading
        viewModelScope.launch {
            val result = repository.derivedValues(today())
            _uiState.value = result.fold(
                onSuccess = { TrainerUiState.Content(it, editSheet = restoredDraft()) },
                onFailure = {
                    if (result.appErrorOrNull() == AppError.Network) TrainerUiState.Offline else TrainerUiState.Error
                },
            )
        }
    }

    private fun restoredDraft(): ActivityLevel? = savedState.get<String>(KEY_DRAFT)
        ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }

    private fun openEdit() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        setDraft(content.editSheet ?: content.values.activityLevel.value ?: ActivityLevel.Moderate)
    }

    private fun pickLevel(level: ActivityLevel) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.editSheet != null) submit(level = level, fromSheet = true)
    }

    private fun setDraft(level: ActivityLevel?) {
        if (level == null) savedState.remove<String>(KEY_DRAFT) else savedState[KEY_DRAFT] = level.name
        _uiState.update { state -> if (state is TrainerUiState.Content) state.copy(editSheet = level) else state }
    }

    private fun submit(level: ActivityLevel?, fromSheet: Boolean) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.confirming) return
        _uiState.value = content.copy(confirming = true)
        viewModelScope.launch {
            val result = repository.confirm(today(), level)
            val values: CoachDerivedValues? = result.getOrNull()
            if (values != null) {
                if (fromSheet) savedState.remove<String>(KEY_DRAFT)
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        TrainerUiState.Content(values, confirming = false, editSheet = null)
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.Confirmed)
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) state.copy(confirming = false) else state
                }
                effectChannel.send(
                    TrainerEffect.ConfirmFailed(fromSheet, offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }

    private companion object {
        const val KEY_DRAFT = "activity_draft"
    }
}
