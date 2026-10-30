package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.port.ExerciseLibrary
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.ExerciseRepository
import io.github.lopezjavier.appfitness.feature.workout.loadExerciseNames
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

/** A block has no "infeasible" outcome, so that kind reads as any other failure. */
private fun blockFailureKindOf(failure: Throwable?): PlanFailureKind =
    planFailureKindOf(failure).let { if (it == PlanFailureKind.Infeasible) PlanFailureKind.Other else it }

private const val KEY_REQUESTING = "block_requesting"
private const val KEY_WEEK = "block_week"
private const val KEY_REASON = "block_reason_exercise"

/**
 * The block preview (US-72). The proposal is always reloaded from the stored rows, so a finished stream survives
 * process death. [savedState] remembers that a request was in flight (a recreated model with no stored block shows
 * [PlanFailureKind.Interrupted] and never asks, and bills, again by itself), the selected week and the open reason
 * sheet. Leaving the screen saves nothing: only [BlockPreviewEvent.Save] does.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One small handler per event; the ports are all needed.
class BlockPreviewViewModel(
    private val planId: String,
    private val blocks: CoachBlockRepository,
    private val exercises: ExerciseRepository,
    private val library: ExerciseLibrary,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<BlockPreviewUiState>(BlockPreviewUiState.Loading)
    val uiState: StateFlow<BlockPreviewUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<BlockPreviewEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        val wasRequesting = savedState.get<Boolean>(KEY_REQUESTING) == true
        viewModelScope.launch {
            val missing = if (wasRequesting) {
                savedState.remove<Boolean>(KEY_REQUESTING)
                BlockPreviewUiState.Failed(PlanFailureKind.Interrupted)
            } else {
                BlockPreviewUiState.Idle
            }
            val stored = blocks.currentBlock(planId)
            val block = stored.getOrNull()
            when {
                stored.isFailure ->
                    _uiState.value =
                        BlockPreviewUiState.Failed(blockFailureKindOf(stored.exceptionOrNull()))

                block != null -> showBlock(block, restore = true)

                else -> _uiState.value = missing
            }
        }
    }

    fun onEvent(event: BlockPreviewEvent) {
        when (event) {
            BlockPreviewEvent.Request -> if (_uiState.value == BlockPreviewUiState.Idle) request()
            BlockPreviewEvent.Retry -> if (_uiState.value is BlockPreviewUiState.Failed) retry()
            is BlockPreviewEvent.SelectWeek -> selectWeek(event.week)
            is BlockPreviewEvent.OpenReason -> setReason(event.key)
            BlockPreviewEvent.CloseReason -> setReason(null)
            BlockPreviewEvent.Save -> save()
        }
    }

    private fun request() {
        viewModelScope.launch { runRequest() }
    }

    private suspend fun runRequest() {
        _uiState.value = BlockPreviewUiState.Requesting
        savedState[KEY_REQUESTING] = true
        val result = blocks.requestBlock(planId, today())
        savedState.remove<Boolean>(KEY_REQUESTING)
        result.fold(
            onSuccess = { showBlock(it, restore = false) },
            onFailure = { _uiState.value = BlockPreviewUiState.Failed(blockFailureKindOf(it)) },
        )
    }

    private fun retry() {
        _uiState.value = BlockPreviewUiState.Loading
        viewModelScope.launch {
            val block = blocks.currentBlock(planId).getOrNull()
            if (block != null) showBlock(block, restore = true) else runRequest()
        }
    }

    private suspend fun showBlock(block: CoachBlock, restore: Boolean) {
        // Names are decoration: an unreadable catalog leaves every exercise with the generic name.
        val names = loadExerciseNames(exercises, library).orEmpty()
        val weeks = block.weeks().map { it.week }
        val savedWeek = savedState.get<Int>(KEY_WEEK)
        val week = savedWeek?.takeIf { it in weeks } ?: weeks.firstOrNull() ?: 1
        val preview = BlockPreviewUiState.Preview(
            block = block,
            exerciseNames = names,
            selectedWeek = week,
            reasonKey = if (restore) savedState.get<String>(KEY_REASON) else null,
        )
        // A key that no longer points to an exercise would open an empty sheet.
        _uiState.value = if (preview.reasonKey != null && preview.reasonExercise == null) {
            savedState.remove<String>(KEY_REASON)
            preview.copy(reasonKey = null)
        } else {
            preview
        }
    }

    private fun selectWeek(week: Int) {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        if (preview.weeks.none { it.week == week }) return
        savedState[KEY_WEEK] = week
        _uiState.value = preview.copy(selectedWeek = week)
    }

    private fun setReason(key: String?) {
        if (key == null) savedState.remove<String>(KEY_REASON) else savedState[KEY_REASON] = key
        _uiState.update { state ->
            if (state is BlockPreviewUiState.Preview) state.copy(reasonKey = key) else state
        }
    }

    private fun save() {
        val preview = _uiState.value as? BlockPreviewUiState.Preview ?: return
        if (preview.saving) return
        _uiState.value = preview.copy(saving = true)
        viewModelScope.launch {
            val result = blocks.saveBlock(planId)
            if (result.isSuccess) {
                savedState.remove<String>(KEY_REASON)
                _uiState.value = BlockPreviewUiState.Closed
                effectChannel.send(BlockPreviewEffect.Saved)
            } else {
                // CoachFailure.Conflict (block already saved or plan no longer open) also lands here: the generic
                // save error is shown and the proposal stays, since nothing more can be saved from this screen.
                _uiState.update { state ->
                    if (state is BlockPreviewUiState.Preview) state.copy(saving = false) else state
                }
                effectChannel.send(
                    BlockPreviewEffect.ActionFailed(offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }
}
