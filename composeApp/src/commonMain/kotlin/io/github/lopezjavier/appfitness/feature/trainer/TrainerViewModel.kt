package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.AppException
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
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
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.model.ProgressionFlagDecision
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.BodyMeasurementRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachChangesRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.GoalChecklist
import io.github.lopezjavier.appfitness.domain.usecase.GoalTargets
import io.github.lopezjavier.appfitness.domain.usecase.NutritionGoalsChecklist
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
import kotlin.math.roundToInt

private const val KEY_REMOVE_BLOCK_CONFIRM = "remove_block_confirm"
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
) : ViewModel() {
    private val cardioEdit = CardioEditState(savedState)

    private val _uiState = MutableStateFlow<TrainerUiState>(TrainerUiState.Loading)
    val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<TrainerEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var loadJob: Job? = null

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
            is TrainerEvent.DecideDeload -> decideDeload(event.accept)
            TrainerEvent.OpenCardioEdit -> openCardioEdit()
            is TrainerEvent.CardioMinutesChanged -> changeCardio(event.minutes)
            TrainerEvent.SaveCardio -> saveCardio()
            TrainerEvent.DismissCardioEdit -> dismissCardioEdit()
            TrainerEvent.RequestRemoveBlock -> requestRemoveBlock()
            TrainerEvent.ConfirmRemoveBlock -> confirmRemoveBlock()
            TrainerEvent.DismissRemoveBlock -> dismissRemoveBlock()
            is TrainerEvent.UndoCoachChange -> undoCoachChange(event.id)
            is TrainerEvent.DecideFlag -> decideFlag(event.id, event.decision)
            is TrainerEvent.OpenRange -> openRange(event.id)
        }
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
        val routineId = row.routineIds.firstOrNull() ?: return
        viewModelScope.launch { effectChannel.send(TrainerEffect.OpenRoutineRange(routineId, row.exerciseId)) }
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
                // The weeks, deload card and block entry all derive from the block: reload the hub.
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

    @Suppress("ReturnCount") // Guards for a missing card, repository or an in-flight decision.
    private fun decideDeload(accept: Boolean) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val proposal = content.deload ?: return
        val repository = coachWeeks ?: return
        if (content.decidingDeload) return
        _uiState.value = content.copy(decidingDeload = true)
        viewModelScope.launch {
            val result = repository.decideDeload(proposal.id, accept)
            if (result.isSuccess) {
                // Accepting marks next week: reload the list; a failed reload keeps the weeks already shown.
                val reloaded = repository.weeks(proposal.planId, today()).getOrNull()
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        state.copy(deload = null, decidingDeload = false, weeks = reloaded ?: state.weeks)
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.DeloadDecided(accept))
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) state.copy(decidingDeload = false) else state
                }
                effectChannel.send(
                    TrainerEffect.DeloadFailed(accept, offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
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
                        decidingDeload = state.decidingDeload,
                        busyChangeId = state.busyChangeId,
                        cardioEdit = state.cardioEdit,
                        removingBlock = state.removingBlock,
                        provisionalCount = fresh.provisionalCount ?: state.provisionalCount,
                    )
                } else {
                    state
                }
            }
        }
    }

    private suspend fun fetch(): TrainerUiState.Content {
        // The count only feeds the "Tus datos" row: a failed read leaves the row without it.
        val provisional = repository.derivedValues(today()).getOrNull()?.provisionalCount()
        // The plan entry and the calorie card never block the screen: a failed read means none.
        val plan = readPlan()
        // A diet-break plan switches the Diario target by plan week; a failure is swallowed like its siblings.
        if (plan is PlanSummary.Accepted) plans?.applyDietBreak(today())
        val proposal = if (plan is PlanSummary.Accepted) plans?.pendingCalorieProposal(today())?.getOrNull() else null
        val block = blocks.summaryFor(plan)
        // Weeks and the deload card need an accepted plan with a saved block; failed reads mean none.
        val savedPlanId = (plan as? PlanSummary.Accepted)?.plan?.id?.takeIf { block is BlockSummary.Saved }
        val deload = savedPlanId?.let { coachWeeks?.pendingDeload(today())?.getOrNull() }
        val planWeeks = savedPlanId?.let { coachWeeks?.weeks(it, today())?.getOrNull() }.orEmpty()
        // The coach changes of the open plan; a failed read hides the section like its siblings.
        val changeRows = plan.id()?.let { readCoachChanges(it) }.orEmpty()
        // A restored confirmation only makes sense while the block is still saved.
        val confirmRemove = savedState.get<Boolean>(KEY_REMOVE_BLOCK_CONFIRM) == true
        if (confirmRemove && block !is BlockSummary.Saved) savedState.remove<Boolean>(KEY_REMOVE_BLOCK_CONFIRM)
        return TrainerUiState.Content(
            plan = plan,
            calorieProposal = proposal,
            block = block,
            weeks = planWeeks,
            deload = deload,
            coachChanges = changeRows,
            provisionalCount = provisional,
            goalsChecklist = goalsChecklistFor(plan),
            cardioEdit = cardioEdit.current((plan as? PlanSummary.Accepted)?.plan?.cardioMinutes()),
            removeBlockConfirm = confirmRemove && block is BlockSummary.Saved,
        )
    }

    /** N1-N4 on the Diario goals in use: only for an accepted plan with edited goals, null when an input is missing. */
    @Suppress("ReturnCount") // Guards: not edited, a repository, or an input is missing.
    private suspend fun goalsChecklistFor(plan: PlanSummary): GoalChecklist? {
        val accepted = (plan as? PlanSummary.Accepted)?.plan ?: return null
        if (!accepted.nutrition.editedByUser) return null
        val saved = goals?.getOwn()?.getOrNull() ?: return null
        val sex = profile?.getBiometrics()?.getOrNull()?.sex ?: return null
        val weightKg = measurements?.getLatestWeight()?.getOrNull()?.weightKg ?: return null
        val kcal = saved.targetKcal
        return NutritionGoalsChecklist(
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

    private suspend fun readPlan(): PlanSummary {
        val plan = plans?.currentPlan()?.getOrNull() ?: return PlanSummary.None
        return if (plan.status == PlanStatus.Accepted) PlanSummary.Accepted(plan) else PlanSummary.Proposed(plan)
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
