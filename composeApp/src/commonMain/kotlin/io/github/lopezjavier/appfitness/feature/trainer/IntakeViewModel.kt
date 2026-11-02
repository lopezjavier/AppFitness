package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.IntakeUpdate
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Para conocerte" (US-81, R-55): the free-text editor only. Saving sends just the text, so the other intake values
 * stay as they are. The half-written text and the discard dialog survive recreation through [savedState] (ADR-40);
 * the stored text is always re-fetched and the draft wins.
 */
class IntakeViewModel(
    private val repository: CoachIntakeRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<IntakeUiState>(IntakeUiState.Loading)
    val uiState: StateFlow<IntakeUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<IntakeEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: IntakeEvent) {
        when (event) {
            IntakeEvent.Retry -> load()

            is IntakeEvent.EditFreeText ->
                update { copy(text = event.text.take(IntakeLimits.MAX_FREE_TEXT), notice = null) }

            IntakeEvent.Save -> save()

            IntakeEvent.Back -> back()

            IntakeEvent.ConfirmDiscard -> confirmDiscard()

            IntakeEvent.DismissDiscard -> update { copy(discardDialog = false) }
        }
    }

    private fun load() {
        _uiState.value = IntakeUiState.Loading
        viewModelScope.launch {
            val result = repository.get()
            _uiState.value = result.fold(
                onSuccess = { intake ->
                    val baseline = intake?.freeText.orEmpty()
                    val draft = savedState.get<String>(KEY_TEXT)
                    IntakeUiState.Content(
                        text = draft ?: baseline,
                        baseline = baseline,
                        discardDialog = draft != null && savedState.get<Boolean>(KEY_DISCARD) == true,
                    )
                },
                onFailure = {
                    if (result.appErrorOrNull() == AppError.Network) IntakeUiState.Offline else IntakeUiState.Error
                },
            )
        }
    }

    private fun update(transform: IntakeUiState.Content.() -> IntakeUiState.Content) {
        _uiState.update { state -> if (state is IntakeUiState.Content) state.transform() else state }
        persist()
    }

    private fun save() {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!content.canSave) return
        update { copy(saving = true, notice = null) }
        viewModelScope.launch {
            val result = repository.save(IntakeUpdate.FreeText(content.text.takeIf { it.isNotBlank() }))
            result.fold(
                onSuccess = {
                    update { copy(baseline = content.text, saving = false) }
                    clearDraft()
                    effectChannel.send(IntakeEffect.Saved)
                },
                onFailure = {
                    val notice = if (result.appErrorOrNull() == AppError.Network) {
                        IntakeNotice.SaveOffline
                    } else {
                        IntakeNotice.SaveFailed
                    }
                    update { copy(saving = false, notice = notice) }
                },
            )
        }
    }

    private fun back() {
        val content = _uiState.value as? IntakeUiState.Content
        if (content?.dirty == true) {
            update { copy(discardDialog = true) }
        } else {
            viewModelScope.launch { effectChannel.send(IntakeEffect.Close) }
        }
    }

    private fun confirmDiscard() {
        update { copy(discardDialog = false) }
        clearDraft()
        viewModelScope.launch { effectChannel.send(IntakeEffect.Close) }
    }

    // --- Restoration (ADR-40): primitives only. ---

    private fun persist() {
        val content = _uiState.value as? IntakeUiState.Content ?: return
        if (!content.dirty) return clearDraft()
        savedState[KEY_TEXT] = content.text
        savedState[KEY_DISCARD] = content.discardDialog
    }

    private fun clearDraft() {
        savedState.remove<String>(KEY_TEXT)
        savedState.remove<Boolean>(KEY_DISCARD)
    }

    private companion object {
        const val KEY_TEXT = "intake_free_text"
        const val KEY_DISCARD = "intake_discard"
    }
}
