package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import kotlinx.coroutines.Job
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
 * "Tus datos": every input the coach reads, plan and block merged, grouped by origin. Read-only apart from the
 * activity-level sheet, which confirms the values with the picked level (as the old Trainer card did).
 */
class YourDataViewModel(
    private val coach: CoachRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<YourDataUiState>(YourDataUiState.Loading)
    val uiState: StateFlow<YourDataUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<YourDataEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var loadJob: Job? = null

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    fun onEvent(event: YourDataEvent) {
        when (event) {
            YourDataEvent.Load, YourDataEvent.Retry -> load()
            YourDataEvent.Refresh -> refresh()
            YourDataEvent.OpenSheet -> openSheet()
            is YourDataEvent.PickLevel -> pickLevel(event.level)
            YourDataEvent.CloseSheet -> setDraft(null)
        }
    }

    private fun load() {
        _uiState.value = YourDataUiState.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch { _uiState.value = fetch() }
    }

    private fun refresh() {
        if (loadJob?.isActive == true) return
        if (_uiState.value !is YourDataUiState.Content) return load()
        loadJob = viewModelScope.launch {
            val fresh = fetch() as? YourDataUiState.Content ?: return@launch
            _uiState.update { state ->
                if (state is YourDataUiState.Content) {
                    fresh.copy(sheet = state.sheet, savingLevel = state.savingLevel)
                } else {
                    state
                }
            }
        }
    }

    /** The plan list is required; a failed block list only drops the block-only rows. */
    private suspend fun fetch(): YourDataUiState {
        val plan = coach.planInputs(PlanInputKind.Plan, today())
        val block = coach.planInputs(PlanInputKind.Block, today())
        val planRows = plan.getOrNull()?.rows()
        val blockRows = block.getOrNull()?.rows()
        return when {
            planRows == null && blockRows == null ->
                if (plan.appErrorOrNull() == AppError.Network) YourDataUiState.Offline else YourDataUiState.Error

            else -> YourDataUiState.Content(
                rows = mergeRows(planRows.orEmpty(), blockRows.orEmpty()),
                sheet = restoredDraft(),
            )
        }
    }

    private fun restoredDraft(): ActivityLevel? = savedState.get<String>(KEY_DRAFT)
        ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }

    private fun openSheet() {
        val content = _uiState.value as? YourDataUiState.Content ?: return
        val text = (
            content.rows.firstOrNull {
                it.key == PlanInputKey.ActivityLevel
            }?.value as? PlanInputValue.Text
            )?.text
        val normalized = text?.filter { it.isLetter() }?.lowercase()
        val current = ActivityLevel.entries.firstOrNull { it.name.lowercase() == normalized }
        setDraft(content.sheet ?: current ?: ActivityLevel.Moderate)
    }

    private fun setDraft(level: ActivityLevel?) {
        if (level == null) savedState.remove<String>(KEY_DRAFT) else savedState[KEY_DRAFT] = level.name
        _uiState.update { state -> if (state is YourDataUiState.Content) state.copy(sheet = level) else state }
    }

    private fun pickLevel(level: ActivityLevel) {
        val content = _uiState.value as? YourDataUiState.Content ?: return
        if (content.sheet == null || content.savingLevel) return
        _uiState.value = content.copy(savingLevel = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), level)
            if (result.isSuccess) {
                savedState.remove<String>(KEY_DRAFT)
                val fresh = fetch() as? YourDataUiState.Content
                _uiState.update { state ->
                    if (state is YourDataUiState.Content) {
                        (fresh ?: state).copy(sheet = null, savingLevel = false)
                    } else {
                        state
                    }
                }
            } else {
                _uiState.update { state ->
                    if (state is YourDataUiState.Content) state.copy(savingLevel = false) else state
                }
                effectChannel.send(YourDataEffect.SaveFailed(result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    private companion object {
        const val KEY_DRAFT = "your_data_activity_draft"
    }
}
