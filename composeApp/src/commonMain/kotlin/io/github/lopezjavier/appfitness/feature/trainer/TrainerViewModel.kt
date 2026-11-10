package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachChangeException
import io.github.lopezjavier.appfitness.domain.model.CoachChangeFailure
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.CoachChangeStatus
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachSummaryRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortFollowUpRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.GoalChecklist
import io.github.lopezjavier.appfitness.domain.usecase.GoalTargets
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadWeeksInPhaseUseCase
import io.github.lopezjavier.appfitness.domain.usecase.LoadWelcomeBack
import io.github.lopezjavier.appfitness.domain.usecase.NutritionGoalsChecklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

private const val KEY_REMOVE_BLOCK_CONFIRM = "remove_block_confirm"
private const val KEY_EXTEND_PICKER = "extend_block_picker"
private const val PERCENT = 100.0

private fun PlanSummary.id(): String? = when (this) {
    is PlanSummary.Proposed -> plan.id
    is PlanSummary.Accepted -> plan.id
    PlanSummary.None -> null
}

/** The stored block of an open plan; a failed read means none, like the plan entry itself. */
private suspend fun CoachBlockRepository?.summaryFor(plan: PlanSummary): BlockSummary {
    val planId = plan.id()
    val block = planId?.let { this?.currentBlock(it)?.getOrNull() }
    return when {
        block == null -> BlockSummary.None
        block.saved -> BlockSummary.Saved(block)
        else -> BlockSummary.Proposed(block)
    }
}

private fun CoachPlan.cardioMinutes(): Int? = nutrition.activity?.cardioMinWeek

private fun CoachDerivedValues.provisionalCount(): Int = listOf(
    activityLevel.provisional,
    totalExpenditure.provisional,
    weightTrend.provisional,
    experience.provisional,
).count { it }

/**
 * Trainer tab root: the plan hub (plan entry, block, pending decisions, weeks) plus the count of provisional values
 * for the "Tus datos" row. Every read degrades to "none" on failure; nothing here blocks the screen. The derived values
 * are read only for that count and are never confirmed from here ("Antes de empezar" confirms).
 */
@Suppress("TooManyFunctions") // One small handler per event.
class TrainerViewModel(
    private val repository: CoachRepository,
    private val clock: Clock,
    private val plans: CoachPlanRepository? = null,
    private val blocks: CoachBlockRepository? = null,
    private val coachWeeks: CoachWeeksRepository? = null,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val goals: NutritionGoalsRepository? = null,
    private val profile: ProfileRepository? = null,
    private val measurements: BodyMeasurementRepository? = null,
    private val coachChanges: CoachChangesRepository? = null,
    private val decideProposal: DecideCalorieProposalUseCase? = plans?.let { DecideCalorieProposalUseCase(it) },
    private val summaries: CoachSummaryRepository? = null,
    private val loadBlockReview: LoadBlockReviewUseCase? = null,
    private val loadWeeksInPhase: LoadWeeksInPhaseUseCase? = null,
    private val loadWelcomeBack: LoadWelcomeBack? = null,
    discomfortFollowUps: DiscomfortFollowUpRepository? = null,
    exerciseNames: ExerciseNameResolver? = null,
) : ViewModel() {
    private val cardioEdit = CardioEditState(savedState)

    private val _uiState = MutableStateFlow<TrainerUiState>(TrainerUiState.Loading)
    val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<TrainerEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var loadJob: Job? = null

    private val discomfort = discomfortFollowUps?.let {
        TrainerDiscomfortController(
            repository = it,
            names = exerciseNames,
            state = _uiState,
            savedState = savedState,
            scope = viewModelScope,
            send = effectChannel::send,
            reloadHub = ::reloadHub,
        )
    }

    /** Fresh read of the whole hub after the swap changed the block's routines. */
    private suspend fun reloadHub() {
        loadJob?.cancel()
        _uiState.value = fetch()
    }

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatch over the events.
    fun onEvent(event: TrainerEvent) {
        when (event) {
            TrainerEvent.Load -> load()
            TrainerEvent.Refresh -> refresh()
            TrainerEvent.ApplyCalorieProposal -> decideCalorieProposal(apply = true)
            TrainerEvent.DeclineCalorieProposal -> decideCalorieProposal(apply = false)
            is TrainerEvent.CancelDeload -> cancelDeload(event.id)
            TrainerEvent.RequestDeload -> requestDeload()
            TrainerEvent.OpenExtendPicker -> openExtendPicker()
            TrainerEvent.DismissExtendPicker -> dismissExtendPicker()
            is TrainerEvent.ExtendBlock -> extendBlock(event.day)
            TrainerEvent.OpenCardioEdit -> openCardioEdit()
            is TrainerEvent.CardioMinutesChanged -> changeCardio(event.minutes)
            is TrainerEvent.ToggleDietBreak -> toggleDietBreak(event.on)
            TrainerEvent.SaveCardio -> saveCardio()
            TrainerEvent.DismissCardioEdit -> dismissCardioEdit()
            TrainerEvent.RequestRemoveBlock -> requestRemoveBlock()
            TrainerEvent.ConfirmRemoveBlock -> confirmRemoveBlock()
            TrainerEvent.DismissRemoveBlock -> dismissRemoveBlock()
            is TrainerEvent.UndoCoachChange -> undoCoachChange(event.id)
            is TrainerEvent.DecideFlag -> decideFlag(event.id, event.decision)
            is TrainerEvent.OpenRange -> openRange(event.id)
            TrainerEvent.OpenNewBlock -> openNewBlock()
            TrainerEvent.OpenMyTraining -> viewModelScope.launch { effectChannel.send(TrainerEffect.OpenMyTraining) }
            is TrainerEvent.OpenSwapPicker -> discomfort?.openPicker(event.exerciseId)
            TrainerEvent.DismissSwapPicker -> discomfort?.dismissPicker()
            is TrainerEvent.PickSwap -> discomfort?.pick(event.newExerciseId)
            is TrainerEvent.DeclineSwap -> discomfort?.decline(event.exerciseId)
            is TrainerEvent.DismissDiscomfortNote -> discomfort?.dismissNote(event.exerciseId)
        }
    }

    private fun openNewBlock() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val allowed = content.canStartNewBlock && !content.blockReviewDue
        val planId = (content.plan as? PlanSummary.Accepted)?.plan?.id?.takeIf { allowed } ?: return
        viewModelScope.launch { effectChannel.send(TrainerEffect.OpenNewBlock(planId)) }
    }

    /** The row to act on, or null when the tab is not showing content, another change is busy or it is not there. */
    @Suppress("ReturnCount") // Guards: no content, busy, or no such row.
    private fun idleRow(id: String): Pair<TrainerUiState.Content, CoachChangeRowUi>? {
        val content = _uiState.value as? TrainerUiState.Content ?: return null
        if (content.busyChangeId != null) return null
        val row = content.coachChanges.firstOrNull { it.id == id } ?: return null
        return content to row
    }

    private fun setBusy(id: String?) {
        _uiState.update { state -> if (state is TrainerUiState.Content) state.copy(busyChangeId = id) else state }
    }

    /** Fresh read of the list after an action; a failed read keeps the rows already shown. */
    private suspend fun reloadCoachChanges() {
        val planId = (_uiState.value as? TrainerUiState.Content)?.plan?.id() ?: return
        val fresh = readCoachChanges(planId) ?: return
        _uiState.update { state -> if (state is TrainerUiState.Content) state.copy(coachChanges = fresh) else state }
    }

    private suspend fun readCoachChanges(planId: String): List<CoachChangeRowUi>? =
        coachChanges?.list(planId, ShownCoachChangeStatuses)?.getOrNull()?.toCoachChangeRows()

    @Suppress("ReturnCount") // Guards for a busy or missing row, repository or a change that is not undoable.
    private fun undoCoachChange(id: String) {
        val (_, row) = idleRow(id) ?: return
        val repository = coachChanges ?: return
        if (!row.undoable) return
        setBusy(id)
        viewModelScope.launch {
            val result = repository.undo(id)
            val undone = result.getOrNull()
            if (undone != null) {
                reloadCoachChanges()
                setBusy(null)
                effectChannel.send(TrainerEffect.CoachChangeUndone(undone.skipped))
            } else {
                val failure = undoFailureOf(result.exceptionOrNull())
                if (failure == CoachChangeUndoFailure.NotFound) reloadCoachChanges()
                setBusy(null)
                effectChannel.send(
                    TrainerEffect.CoachChangeUndoFailed(failure, offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }

    private fun undoFailureOf(error: Throwable?): CoachChangeUndoFailure = when {
        error is CoachChangeException &&
            (error.failure == CoachChangeFailure.Conflict || error.failure == CoachChangeFailure.NotUndoable) ->
            CoachChangeUndoFailure.Conflict

        error is AppException && error.error == AppError.NotFound -> CoachChangeUndoFailure.NotFound

        else -> CoachChangeUndoFailure.Other
    }

    @Suppress("ReturnCount") // Guards for a busy or missing row, repository or a row that is not a pending flag.
    private fun decideFlag(id: String, decision: ProgressionFlagDecision) {
        val (_, row) = idleRow(id) ?: return
        val repository = coachChanges ?: return
        if (row.kind != CoachChangeKind.LoadFlag || row.status != CoachChangeStatus.Pending) return
        setBusy(id)
        viewModelScope.launch {
            val result = repository.decide(id, decision)
            if (result.isSuccess) {
                reloadCoachChanges()
                setBusy(null)
                effectChannel.send(TrainerEffect.FlagDecided(decision))
            } else {
                val error = result.exceptionOrNull()
                val notPending = error is CoachChangeException && error.failure == CoachChangeFailure.NotPending
                if (notPending || undoFailureOf(error) == CoachChangeUndoFailure.NotFound) reloadCoachChanges()
                setBusy(null)
                effectChannel.send(
                    TrainerEffect.FlagDecideFailed(
                        offline = result.appErrorOrNull() == AppError.Network,
                        notPending = notPending,
                    ),
                )
            }
        }
    }

    @Suppress("ReturnCount") // Guards for a missing row or one that is not a no-range flag.
    private fun openRange(id: String) {
        val row = (_uiState.value as? TrainerUiState.Content)?.coachChanges?.firstOrNull { it.id == id } ?: return
        if (row.kind != CoachChangeKind.NoRangeFlag || row.status != CoachChangeStatus.Pending) return
        val routineId = row.routineIds.firstOrNull()
        val exerciseId = row.exerciseId
        viewModelScope.launch {
            effectChannel.send(
                if (routineId != null && exerciseId != null) {
                    TrainerEffect.OpenRoutineRange(routineId, exerciseId)
                } else {
                    TrainerEffect.RangeUnavailable
                },
            )
        }
    }

    private fun requestRemoveBlock() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.block !is BlockSummary.Saved) return
        savedState[KEY_REMOVE_BLOCK_CONFIRM] = true
        _uiState.value = content.copy(removeBlockConfirm = true)
    }

    private fun dismissRemoveBlock() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.removingBlock) return
        savedState.remove<Boolean>(KEY_REMOVE_BLOCK_CONFIRM)
        _uiState.value = content.copy(removeBlockConfirm = false)
    }

    @Suppress("ReturnCount") // Guards for a closed dialog, a missing repository or plan, or a removal in flight.
    private fun confirmRemoveBlock() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (!content.removeBlockConfirm || content.removingBlock) return
        val repository = blocks ?: return
        val planId = when (val plan = content.plan) {
            is PlanSummary.Accepted -> plan.plan.id
            is PlanSummary.Proposed -> plan.plan.id
            PlanSummary.None -> return
        }
        _uiState.value = content.copy(removingBlock = true)
        viewModelScope.launch {
            val result = repository.removeBlock(planId)
            savedState.remove<Boolean>(KEY_REMOVE_BLOCK_CONFIRM)
            if (result.isSuccess) {
                // The weeks, deload rows and block entry all derive from the block: reload the hub.
                loadJob?.cancel()
                val fresh = fetch()
                _uiState.value = fresh
                effectChannel.send(TrainerEffect.BlockRemoved)
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        state.copy(removeBlockConfirm = false, removingBlock = false)
                    } else {
                        state
                    }
                }
                effectChannel.send(
                    TrainerEffect.RemoveBlockFailed(offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }

    private fun openExtendPicker() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.pause?.extendable != true || content.extendingBlock) return
        savedState[KEY_EXTEND_PICKER] = true
        _uiState.value = content.copy(extendPickerOpen = true)
    }

    private fun dismissExtendPicker() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        savedState.remove<Boolean>(KEY_EXTEND_PICKER)
        _uiState.value = content.copy(extendPickerOpen = false)
    }

    /** Sets the last day of the running block; the picker closes on confirm whatever the outcome. */
    @Suppress("ReturnCount") // Guards: no content, busy, no repository or plan, not extendable, outside the limits.
    private fun extendBlock(day: LocalDate) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.extendingBlock) return
        savedState.remove<Boolean>(KEY_EXTEND_PICKER)
        val pause = content.pause
        val repository = coachWeeks
        val planId = (content.plan as? PlanSummary.Accepted)?.plan?.id
        val min = pause?.minEnd
        val max = pause?.maxEnd
        val allowed = pause?.extendable == true && min != null && max != null && day >= min && day <= max
        if (repository == null || planId == null || !allowed) {
            _uiState.value = content.copy(extendPickerOpen = false)
            return
        }
        _uiState.value = content.copy(extendPickerOpen = false, extendingBlock = true)
        viewModelScope.launch {
            val result = repository.setBlockEnd(planId, day, today())
            val updated = result.getOrNull()
            if (updated != null) {
                val weeks = repository.weeks(planId, today()).getOrNull()
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        state.copy(pause = updated, weeks = weeks ?: state.weeks, extendingBlock = false)
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.BlockEndChanged)
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) state.copy(extendingBlock = false) else state
                }
                effectChannel.send(TrainerEffect.BlockEndFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    private fun openCardioEdit() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val saved = (content.plan as? PlanSummary.Accepted)?.plan?.cardioMinutes() ?: return
        cardioEdit.open(saved)
        _uiState.value = content.copy(cardioEdit = cardioEdit.current(saved))
    }

    private fun changeCardio(minutes: Int) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val edit = content.cardioEdit?.takeUnless { it.saving } ?: return
        cardioEdit.change(minutes)
        _uiState.value = content.copy(cardioEdit = cardioEdit.current(edit.saved))
    }

    private fun dismissCardioEdit() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.cardioEdit?.saving != false) return
        cardioEdit.close()
        _uiState.value = content.copy(cardioEdit = null)
    }

    @Suppress("ReturnCount") // Guards for a missing sheet, repository or plan.
    private fun saveCardio() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val edit = content.cardioEdit?.takeIf { it.canSave } ?: return
        val repository = plans ?: return
        val planId = (content.plan as? PlanSummary.Accepted)?.plan?.id ?: return
        _uiState.value = content.copy(cardioEdit = edit.copy(saving = true))
        viewModelScope.launch {
            val result = repository.setCardioMinutes(planId, edit.draft)
            val updated = result.getOrNull()
            if (updated != null) {
                cardioEdit.close()
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        state.copy(plan = PlanSummary.Accepted(updated), cardioEdit = null)
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.CardioSaved)
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        state.copy(cardioEdit = cardioEdit.current(edit.saved))
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.CardioFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    /** Live switch of "Tu plan": only an accepted cut the user did not edit; the stored plan value is the truth. */
    @Suppress("ReturnCount") // Guards: no content, already saving, no repository, not an editable accepted cut.
    private fun toggleDietBreak(on: Boolean) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.dietBreakSaving) return
        val repository = plans ?: return
        val plan = (content.plan as? PlanSummary.Accepted)?.plan ?: return
        if (plan.phase != Phase.Cut || plan.nutrition.editedByUser) return
        _uiState.value = content.copy(dietBreakSaving = true)
        viewModelScope.launch {
            val result = repository.setDietBreak(plan.id, today(), on)
            val updated = result.getOrNull()
            _uiState.update { state ->
                when {
                    state !is TrainerUiState.Content -> state
                    updated != null -> state.copy(plan = PlanSummary.Accepted(updated), dietBreakSaving = false)
                    else -> state.copy(dietBreakSaving = false)
                }
            }
            if (updated != null) {
                // Inside week 4 the Diario target follows the option at once; a failure is swallowed like on open.
                repository.applyDietBreak(today())
                effectChannel.send(TrainerEffect.DietBreakSaved)
            } else {
                effectChannel.send(TrainerEffect.DietBreakFailed(offline = result.appErrorOrNull() == AppError.Network))
            }
        }
    }

    @Suppress("ReturnCount") // Guards for a busy or missing row, repository or a row that is not an applied deload.
    private fun cancelDeload(id: String) {
        val (content, row) = idleRow(id) ?: return
        val repository = coachWeeks ?: return
        if (!row.cancellable || content.requestingDeload) return
        setBusy(id)
        viewModelScope.launch {
            val result = repository.cancelDeload(id)
            if (result.isSuccess) {
                reloadDeloadState()
                setBusy(null)
                effectChannel.send(TrainerEffect.DeloadCancelled)
            } else {
                // A stale row (cancelled elsewhere, or gone) is replaced by what the server holds now.
                if (result.appErrorOrNull() != AppError.Network) reloadDeloadState()
                setBusy(null)
                effectChannel.send(
                    TrainerEffect.DeloadCancelFailed(
                        offline =
                        result.appErrorOrNull() == AppError.Network,
                    ),
                )
            }
        }
    }

    @Suppress("ReturnCount") // Guards for a missing repository, a disallowed request or one already in flight.
    private fun requestDeload() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val repository = coachWeeks ?: return
        if (!content.canRequestDeload || content.requestingDeload || content.busyChangeId != null) return
        _uiState.value = content.copy(requestingDeload = true)
        viewModelScope.launch {
            val result = repository.requestDeload()
            if (result.isSuccess) {
                reloadDeloadState()
                setRequesting(false)
                effectChannel.send(TrainerEffect.DeloadRequested)
            } else {
                val error = result.exceptionOrNull()
                val notAvailable =
                    error is CoachChangeException && error.failure == CoachChangeFailure.DeloadNotAvailable
                if (notAvailable) reloadDeloadState()
                setRequesting(false)
                effectChannel.send(
                    TrainerEffect.DeloadRequestFailed(
                        offline = result.appErrorOrNull() == AppError.Network,
                        notAvailable = notAvailable,
                    ),
                )
            }
        }
    }

    private fun setRequesting(requesting: Boolean) {
        _uiState.update { state ->
            if (state is TrainerUiState.Content) state.copy(requestingDeload = requesting) else state
        }
    }

    /** Fresh read of the weeks and the change rows after a deload action; a failed read keeps what is shown. */
    private suspend fun reloadDeloadState() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val planId = (content.plan as? PlanSummary.Accepted)?.plan?.id
        val fresh = planId?.let { coachWeeks?.weeks(it, today())?.getOrNull() }
        _uiState.update { state ->
            if (state is TrainerUiState.Content) state.copy(weeks = fresh ?: state.weeks) else state
        }
        reloadCoachChanges()
    }

    private fun load() {
        _uiState.value = TrainerUiState.Loading
        loadJob?.cancel()
        loadJob = viewModelScope.launch { _uiState.value = fetch() }
    }

    /**
     * Silent reload (the tab came back to the foreground): keeps the shown Content and any in-flight decision, and
     * keeps nothing else stale. Coalesced with a fetch already in flight, so the calorie-change evaluation never runs
     * twice. Without Content it is a regular load.
     */
    private fun refresh() {
        if (loadJob?.isActive == true) return
        if (_uiState.value !is TrainerUiState.Content) return load()
        loadJob = viewModelScope.launch {
            val fresh = fetch()
            _uiState.update { state ->
                if (state is TrainerUiState.Content) {
                    fresh.copy(
                        decidingChange = state.decidingChange,
                        requestingDeload = state.requestingDeload,
                        extendPickerOpen = state.extendPickerOpen && fresh.pause?.extendable == true,
                        extendingBlock = state.extendingBlock,
                        busyChangeId = state.busyChangeId,
                        busyFollowUpExerciseId = state.busyFollowUpExerciseId,
                        cardioEdit = state.cardioEdit,
                        dietBreakSaving = state.dietBreakSaving,
                        removingBlock = state.removingBlock,
                        provisionalCount = fresh.provisionalCount ?: state.provisionalCount,
                    )
                } else {
                    state
                }
            }
        }
    }

    /**
     * One read of the whole hub. The plan-dependent chain keeps its order (plan, diet break, proposal; block; deload
     * evaluation; then weeks, pause and change rows); every read that does not depend on a previous answer runs beside
     * it, so the screen waits for the longest chain, not for the sum. A failed read is a [Result.failure] that only
     * hides its own piece; cancellation still propagates.
     */
    private suspend fun fetch(): TrainerUiState.Content = supervisorScope {
        // The count only feeds the "Tus datos" row: a failed read leaves the row without it.
        val provisional = async { repository.derivedValues(today()).getOrNull()?.provisionalCount() }
        // A failed read just hides the "Resumen semanal" row.
        val hasWeekSummary = async { summaries?.getLatest(today())?.getOrNull() != null }
        // The review opens (idempotently) once the block's last day has passed; a failed read hides the card.
        val blockReviewDue = async { loadBlockReview?.invoke(today())?.getOrNull() != null }
        // The swap offer and the health note; a failed read hides them.
        val followUps = async { discomfort?.followUps().orEmpty() }
        // The plan entry and the calorie card never block the screen: a failed read means none.
        val plan = readPlan()
        // A diet-break plan switches the Diario target by plan week, so it ends before anything reads that target.
        val dietBreak = async { if (plan is PlanSummary.Accepted) plans?.applyDietBreak(today()) }
        val block = blocks.summaryFor(plan)
        dietBreak.await()
        val proposal = async { proposalFor(plan) }
        val checklist = goalsChecklistAsync(plan)
        // A failed read hides the line ("Llevas n semanas en esta fase"), never "0 semanas".
        val weeksInPhase = async {
            if (plan is PlanSummary.Accepted) loadWeeksInPhase?.invoke(today())?.getOrNull() else null
        }
        val welcomeBack = async { welcomeBackFor(plan, block) }
        // Assumption: the deload rule written by evaluateDeload does not change what welcomeBack and weeksInPhase read.
        val hub = hubOf(plan, block)
        // A restored confirmation only makes sense while the block is still saved.
        val confirmRemove = savedState.get<Boolean>(KEY_REMOVE_BLOCK_CONFIRM) == true
        if (confirmRemove && block !is BlockSummary.Saved) savedState.remove<Boolean>(KEY_REMOVE_BLOCK_CONFIRM)
        // Likewise a restored date picker only makes sense while the block can still be changed.
        val extendPicker = savedState.get<Boolean>(KEY_EXTEND_PICKER) == true && hub.pause?.extendable == true
        if (!extendPicker) savedState.remove<Boolean>(KEY_EXTEND_PICKER)
        val shownFollowUps = followUps.await()
        TrainerUiState.Content(
            plan = plan,
            calorieProposal = proposal.await(),
            block = block,
            weeks = hub.weeks,
            pause = hub.pause,
            extendPickerOpen = extendPicker,
            coachChanges = hub.changes,
            provisionalCount = provisional.await(),
            hasWeekSummary = hasWeekSummary.await(),
            blockReviewDue = blockReviewDue.await(),
            welcomeBack = welcomeBack.await(),
            goalsChecklist = checklist.await(),
            cardioEdit = cardioEdit.current((plan as? PlanSummary.Accepted)?.plan?.cardioMinutes()),
            removeBlockConfirm = confirmRemove && block is BlockSummary.Saved,
            weeksInPhase = weeksInPhase.await(),
            discomfortFollowUps = shownFollowUps,
            swapPicker = discomfort?.restoredPicker(shownFollowUps),
        )
    }

    private class HubReads(val weeks: List<PlanWeek>, val pause: BlockPause?, val changes: List<CoachChangeRowUi>)

    /**
     * Weeks and the deload rule need an accepted plan with a saved block; failed reads mean none. The rule applies a
     * deload on its own (plan week 4), so it runs before the weeks, the pause and the change rows are read.
     */
    private suspend fun hubOf(plan: PlanSummary, block: BlockSummary): HubReads = coroutineScope {
        val savedPlanId = (plan as? PlanSummary.Accepted)?.plan?.id?.takeIf { block is BlockSummary.Saved }
        savedPlanId?.let { coachWeeks?.evaluateDeload(today()) }
        val weeks = async { savedPlanId?.let { coachWeeks?.weeks(it, today())?.getOrNull() }.orEmpty() }
        // The pause and the moved end date; a failed read hides them without hiding the weeks.
        val pause = async { savedPlanId?.let { coachWeeks?.blockPause(it, today())?.getOrNull() } }
        // The coach changes of the open plan; a failed read hides the section like its siblings.
        val changes = async { plan.id()?.let { readCoachChanges(it) }.orEmpty() }
        HubReads(weeks.await(), pause.await(), changes.await())
    }

    private suspend fun proposalFor(plan: PlanSummary): CalorieProposal? =
        if (plan is PlanSummary.Accepted) plans?.pendingCalorieProposal(today())?.getOrNull() else null

    private suspend fun welcomeBackFor(plan: PlanSummary, block: BlockSummary): Boolean {
        val accepted = (plan as? PlanSummary.Accepted)?.plan?.takeIf { block is BlockSummary.Saved } ?: return false
        return loadWelcomeBack?.invoke(accepted)?.getOrNull() == true
    }

    /** N1-N4 on the Diario goals in use: only for an accepted plan with edited goals, null when an input is missing. */
    @Suppress("ReturnCount") // Guards: not edited, a repository, or an input is missing.
    private fun CoroutineScope.goalsChecklistAsync(plan: PlanSummary): Deferred<GoalChecklist?> = async {
        val accepted = (plan as? PlanSummary.Accepted)?.plan ?: return@async null
        if (!accepted.nutrition.editedByUser) return@async null
        val savedRead = async { goals?.getOwn()?.getOrNull() }
        val sexRead = async { profile?.getBiometrics()?.getOrNull()?.sex }
        val weightRead = async { measurements?.getLatestWeight()?.getOrNull()?.weightKg }
        val saved = savedRead.await() ?: return@async null
        val sex = sexRead.await() ?: return@async null
        val weightKg = weightRead.await() ?: return@async null
        val kcal = saved.targetKcal
        NutritionGoalsChecklist(
            targets = GoalTargets(
                targetKcal = kcal,
                proteinG = gramsOf(kcal, saved.split, Macronutrient.Protein),
                fatG = gramsOf(kcal, saved.split, Macronutrient.Fat),
            ),
            weightKg = weightKg,
            sex = sex,
            phase = accepted.phase,
            expenditureKcal = accepted.nutrition.expenditureKcal,
        )
    }

    private fun gramsOf(kcal: Int, split: MacroSplit, macro: Macronutrient): Int =
        (kcal * split[macro] / PERCENT / macro.kcalPerGram.toDouble()).roundToInt()

    /**
     * A goal-switch proposal (stored by "Bloque nuevo" until the new block is saved) is never the plan entry: the hub
     * keeps showing the accepted plan, and no proposal card appears.
     */
    private suspend fun readPlan(): PlanSummary {
        val current = plans?.currentPlan()?.getOrNull() ?: return PlanSummary.None
        val plan = if (current.fromGoalSwitch) plans?.acceptedPlan()?.getOrNull() else current
        return when {
            plan == null -> PlanSummary.None
            plan.status == PlanStatus.Accepted -> PlanSummary.Accepted(plan)
            else -> PlanSummary.Proposed(plan)
        }
    }

    @Suppress("ReturnCount") // Guards for a missing card, use case or an in-flight decision.
    private fun decideCalorieProposal(apply: Boolean) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val proposal = content.calorieProposal ?: return
        val decide = decideProposal ?: return
        if (content.decidingChange) return
        _uiState.value = content.copy(decidingChange = true)
        viewModelScope.launch {
            val result = if (apply) decide.apply(proposal.id, today()) else decide.notNow(proposal.id, today())
            if (result.isSuccess) {
                // Applying changes the plan's calories: reload the summary.
                val plan = readPlan()
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        val kept = if (plan is PlanSummary.None) state.plan else plan
                        state.copy(calorieProposal = null, decidingChange = false, plan = kept)
                    } else {
                        state
                    }
                }
                effectChannel.send(
                    if (apply) TrainerEffect.CalorieProposalApplied else TrainerEffect.CalorieProposalDeclined,
                )
            } else {
                if ((result.exceptionOrNull() as? CoachException)?.failure == CoachFailure.Conflict) {
                    // Already decided elsewhere (55000): show what the server holds now.
                    loadJob?.cancel()
                    _uiState.value = fetch()
                } else {
                    _uiState.update { state ->
                        if (state is TrainerUiState.Content) state.copy(decidingChange = false) else state
                    }
                }
                effectChannel.send(
                    TrainerEffect.CalorieProposalFailed(apply, offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }
}
