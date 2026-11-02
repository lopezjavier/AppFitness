package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
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
 * "Antes de empezar" (US-78): reads the inputs of a plan or block creation. Only [BeforeStartEvent.Create] and a level
 * picked in the sheet write anything (they confirm the provisional values); leaving writes nothing. The route
 * arguments (`mode`, `planId`, `replacingPlanId`) and the open sheet's draft survive recreation through
 * [savedState]; the inputs are always re-fetched.
 */
@Suppress("TooManyFunctions") // One small handler per event.
class BeforeStartViewModel(
    private val coach: CoachRepository,
    private val intakeRepo: CoachIntakeRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mode = BeforeStartMode.fromArgument(savedState.get<String>(KEY_MODE))
    private val planId: String? = savedState.get<String>(KEY_PLAN_ID)
    private val replacingPlanId: String? = savedState.get<String>(KEY_REPLACING)

    private val _uiState = MutableStateFlow<BeforeStartUiState>(BeforeStartUiState.Loading)
    val uiState: StateFlow<BeforeStartUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<BeforeStartEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var loadJob: Job? = null

    /** The years and maintenance sheets (R-55); saving one value refreshes the rows. */
    private val rowEditor = IntakeRowEditor(intakeRepo, viewModelScope, savedState) { saved ->
        _uiState.update { state ->
            if (state is BeforeStartUiState.Content) state.copy(intake = IntakeSummary.of(saved)) else state
        }
        refresh()
    }
    val editor: StateFlow<IntakeRowEditorState> = rowEditor.state

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    fun onEvent(event: BeforeStartEvent) {
        when (event) {
            BeforeStartEvent.Load, BeforeStartEvent.Retry -> load()
            BeforeStartEvent.Refresh -> refresh()
            BeforeStartEvent.OpenSheet -> openSheet()
            is BeforeStartEvent.PickLevel -> pickLevel(event.level)
            BeforeStartEvent.CloseSheet -> setDraft(null)
            BeforeStartEvent.Create -> create(minimal = false)
            BeforeStartEvent.CreateMinimal -> create(minimal = true)
        }
    }

    fun onEditorEvent(event: IntakeRowEvent) = rowEditor.onEvent(event)

    private fun load() {
        _uiState.value = BeforeStartUiState.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = fetch()
        }
    }

    /** Silent reload (back from an editor): keeps the shown content and any in-flight action. */
    private fun refresh() {
        if (loadJob?.isActive == true) return
        if (_uiState.value !is BeforeStartUiState.Content) return load()
        loadJob = viewModelScope.launch {
            val fresh = fetch() as? BeforeStartUiState.Content ?: return@launch
            _uiState.update { state ->
                if (state is BeforeStartUiState.Content) {
                    fresh.copy(sheet = state.sheet, creating = state.creating, savingLevel = state.savingLevel)
                } else {
                    state
                }
            }
        }
    }

    private suspend fun fetch(): BeforeStartUiState {
        val result = coach.planInputs(mode.kind, today())
        return result.fold(
            onSuccess = {
                // The intake only enriches the rows: a failed read leaves them empty.
                val intake = intakeRepo.get().getOrNull()
                BeforeStartUiState.Content(
                    it,
                    mode,
                    planId,
                    replacingPlanId,
                    sheet = restoredDraft(),
                    intake = IntakeSummary.of(intake),
                )
            },
            onFailure = {
                if (result.appErrorOrNull() ==
                    AppError.Network
                ) {
                    BeforeStartUiState.Offline
                } else {
                    BeforeStartUiState.Error
                }
            },
        )
    }

    @Suppress("ReturnCount") // Guards for a missing content or a call in flight.
    private fun create(minimal: Boolean) {
        val content = _uiState.value as? BeforeStartUiState.Content ?: return
        if (!content.canCreate || (minimal && !content.canCreateMinimal)) return
        val created = BeforeStartEffect.Created(mode, planId, replacingPlanId, minimal)
        if (!content.hasProvisional) {
            viewModelScope.launch { effectChannel.send(created) }
            return
        }
        _uiState.value = content.copy(creating = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), null)
            _uiState.update { state ->
                if (state is BeforeStartUiState.Content) state.copy(creating = false) else state
            }
            effectChannel.send(
                if (result.isSuccess) {
                    created
                } else {
                    BeforeStartEffect.CreateFailed(result.appErrorOrNull() == AppError.Network)
                },
            )
        }
    }

    private fun restoredDraft(): ActivityLevel? = savedState.get<String>(KEY_DRAFT)
        ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }

    private fun openSheet() {
        val content = _uiState.value as? BeforeStartUiState.Content ?: return
        setDraft(content.sheet ?: content.currentLevel() ?: ActivityLevel.Moderate)
    }

    private fun BeforeStartUiState.Content.currentLevel(): ActivityLevel? {
        val text = (rows.firstOrNull { it.key == PlanInputKey.ActivityLevel }?.value as? PlanInputValue.Text)?.text
        val normalized = text?.filter { it.isLetter() }?.lowercase()
        return ActivityLevel.entries.firstOrNull { it.name.lowercase() == normalized }
    }

    private fun setDraft(level: ActivityLevel?) {
        if (level == null) savedState.remove<String>(KEY_DRAFT) else savedState[KEY_DRAFT] = level.name
        _uiState.update { state -> if (state is BeforeStartUiState.Content) state.copy(sheet = level) else state }
    }

    private fun pickLevel(level: ActivityLevel) {
        val content = _uiState.value as? BeforeStartUiState.Content ?: return
        if (content.sheet == null || content.savingLevel || content.creating) return
        _uiState.value = content.copy(savingLevel = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), level)
            if (result.isSuccess) {
                savedState.remove<String>(KEY_DRAFT)
                val reloaded = coach.planInputs(mode.kind, today()).getOrNull()
                _uiState.update { state ->
                    if (state is BeforeStartUiState.Content) {
                        state.copy(inputs = reloaded ?: state.inputs, savingLevel = false, sheet = null)
                    } else {
                        state
                    }
                }
            } else {
                _uiState.update { state ->
                    if (state is BeforeStartUiState.Content) state.copy(savingLevel = false) else state
                }
                effectChannel.send(BeforeStartEffect.CreateFailed(result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_PLAN_ID = "planId"
        const val KEY_REPLACING = "replacingPlanId"
        const val KEY_DRAFT = "before_start_activity_draft"
    }
}
