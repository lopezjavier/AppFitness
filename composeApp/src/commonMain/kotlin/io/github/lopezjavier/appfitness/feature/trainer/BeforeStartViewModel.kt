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
import io.github.lopezjavier.appfitness.domain.repository.RoutineRepository
import io.github.lopezjavier.appfitness.domain.usecase.OpenNewBlockUseCase
import io.github.lopezjavier.appfitness.domain.usecase.PreviewGoalSwitchUseCase
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
    private val routines: RoutineRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val openNewBlock: OpenNewBlockUseCase? = null,
    private val previewGoalSwitch: PreviewGoalSwitchUseCase? = null,
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
            BeforeStartEvent.Create -> create()
            is BeforeStartEvent.PickStart -> pickStart(event.option)
            is BeforeStartEvent.PickFolder -> pickFolder(event.folderId)
            BeforeStartEvent.CloseChoice -> setChoice(StartChoiceStep.None)
            BeforeStartEvent.RetryFolders -> loadFolders()
        }
    }

    fun onEditorEvent(event: IntakeRowEvent) = rowEditor.onEvent(event)

    private fun load() {
        _uiState.value = BeforeStartUiState.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = fetch()
            if ((_uiState.value as? BeforeStartUiState.Content)?.startChoice == StartChoiceStep.Folders) loadFolders()
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
                    fresh.copy(
                        sheet = state.sheet,
                        creating = state.creating,
                        savingLevel = state.savingLevel,
                        startChoice = state.startChoice,
                        folderPicker = state.folderPicker,
                    )
                } else {
                    state
                }
            }
        }
    }

    private suspend fun fetch(): BeforeStartUiState {
        if (mode != BeforeStartMode.NewBlock) return readInputs()
        return when (val prepared = prepareNewBlock()) {
            is NewBlockPrep.Ready -> when (val inputs = readInputs()) {
                is BeforeStartUiState.Content ->
                    inputs.copy(goalChange = prepared.note, previewPlanId = prepared.planId)

                else -> inputs
            }

            is NewBlockPrep.Stop -> prepared.state
        }
    }

    /** What "Bloque nuevo" reads before the inputs: the entry, then (goal changed) the goal-switch preview. */
    private sealed interface NewBlockPrep {
        data class Ready(val note: GoalChangeNote?, val planId: String?) : NewBlockPrep

        data class Stop(val state: BeforeStartUiState) : NewBlockPrep
    }

    private suspend fun prepareNewBlock(): NewBlockPrep {
        val opened = openNewBlock?.invoke(today())
            ?: return NewBlockPrep.Stop(BeforeStartUiState.Error)
        val entry = opened.getOrNull()
        return when {
            opened.isFailure -> NewBlockPrep.Stop(failedState(opened.appErrorOrNull()))

            entry == null -> {
                // No saved block: nothing to start.
                effectChannel.send(BeforeStartEffect.Close)
                NewBlockPrep.Stop(BeforeStartUiState.Loading)
            }

            !entry.goal.changed -> NewBlockPrep.Ready(null, null)

            else -> previewGoal(entry.goal.currentKcal)
        }
    }

    private suspend fun previewGoal(entryKcal: Int?): NewBlockPrep {
        val result = previewGoalSwitch?.invoke(today()) ?: Result.failure(IllegalStateException("No preview"))
        val preview = result.getOrNull()
        if (preview == null) {
            val offline = result.appErrorOrNull() == AppError.Network
            effectChannel.send(BeforeStartEffect.NewBlockPrepareFailed(offline))
            return NewBlockPrep.Stop(failedState(result.appErrorOrNull()))
        }
        return NewBlockPrep.Ready(
            GoalChangeNote(preview.currentKcal ?: entryKcal, preview.newKcal, preview.phase),
            preview.planId,
        )
    }

    private fun failedState(error: AppError?): BeforeStartUiState =
        if (error == AppError.Network) BeforeStartUiState.Offline else BeforeStartUiState.Error

    private suspend fun readInputs(): BeforeStartUiState {
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
                    startChoice = restoredChoice(),
                )
            },
            onFailure = { failedState(result.appErrorOrNull()) },
        )
    }

    @Suppress("ReturnCount") // Guards for a missing content or a call in flight.
    private fun create() {
        val content = _uiState.value as? BeforeStartUiState.Content ?: return
        if (!content.canCreate) return
        if (!content.hasProvisional) {
            viewModelScope.launch { proceed() }
            return
        }
        _uiState.value = content.copy(creating = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), null)
            _uiState.update { state ->
                if (state is BeforeStartUiState.Content) state.copy(creating = false) else state
            }
            if (result.isSuccess) {
                proceed()
            } else {
                effectChannel.send(BeforeStartEffect.CreateFailed(result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    /**
     * A plan goes straight on; a block first asks how it starts (US-93); "Bloque nuevo" opens the next-block builder
     * on the goal-switch plan (else the accepted one), with the accepted plan as the previous block.
     */
    private suspend fun proceed() {
        when (mode) {
            BeforeStartMode.Block -> setChoice(StartChoiceStep.Choice)

            BeforeStartMode.NewBlock -> {
                val preview = (_uiState.value as? BeforeStartUiState.Content)?.previewPlanId
                effectChannel.send(BeforeStartEffect.Created(mode, preview ?: planId, planId))
            }

            BeforeStartMode.Plan -> effectChannel.send(BeforeStartEffect.Created(mode, planId, replacingPlanId))
        }
    }

    private fun pickStart(option: StartOption) {
        if ((_uiState.value as? BeforeStartUiState.Content)?.startChoice != StartChoiceStep.Choice) return
        when (option) {
            StartOption.Scratch -> {
                setChoice(StartChoiceStep.None)
                effectChannel.trySend(BeforeStartEffect.Created(mode, planId, replacingPlanId))
            }

            StartOption.Folder -> {
                setChoice(StartChoiceStep.Folders)
                loadFolders()
            }
        }
    }

    private fun pickFolder(folderId: String) {
        val picker = (_uiState.value as? BeforeStartUiState.Content)?.folderPicker as? FolderPickerState.Loaded
        if (picker?.folders?.any { it.id == folderId } != true) return
        effectChannel.trySend(BeforeStartEffect.OpenFolderBlock(planId, folderId))
    }

    private fun setChoice(step: StartChoiceStep) {
        if (step == StartChoiceStep.None) savedState.remove<String>(KEY_CHOICE) else savedState[KEY_CHOICE] = step.name
        _uiState.update { state ->
            if (state is BeforeStartUiState.Content) {
                state.copy(startChoice = step, folderPicker = FolderPickerState.Loading)
            } else {
                state
            }
        }
    }

    private fun loadFolders() {
        updateContent { it.copy(folderPicker = FolderPickerState.Loading) }
        viewModelScope.launch {
            val result = routines.listFolders()
            val picker = result.fold(
                onSuccess = { FolderPickerState.Loaded(it) },
                onFailure = { FolderPickerState.Failed(result.appErrorOrNull() == AppError.Network) },
            )
            updateContent { it.copy(folderPicker = picker) }
        }
    }

    private fun updateContent(transform: (BeforeStartUiState.Content) -> BeforeStartUiState.Content) {
        _uiState.update { if (it is BeforeStartUiState.Content) transform(it) else it }
    }

    private fun restoredChoice(): StartChoiceStep = savedState.get<String>(KEY_CHOICE)
        ?.let { name -> StartChoiceStep.entries.firstOrNull { it.name == name } }
        ?.takeIf { mode == BeforeStartMode.Block }
        ?: StartChoiceStep.None

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
        const val KEY_CHOICE = "before_start_start_choice"
    }
}
