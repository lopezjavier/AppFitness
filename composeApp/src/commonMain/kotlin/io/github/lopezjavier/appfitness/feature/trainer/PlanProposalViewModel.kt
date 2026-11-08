package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.CutRate
import io.github.lopezjavier.appfitness.domain.model.PlanInputKey
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
import io.github.lopezjavier.appfitness.domain.model.PlanInputValue
import io.github.lopezjavier.appfitness.domain.model.PlanInputs
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachIntakeRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
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

private const val KEY_REQUESTING = "plan_requesting"
private const val KEY_REPLACING = "replacingPlanId"
private const val KEY_ACTIVITY_DRAFT = "plan_proposal_activity_draft"
private const val KEY_BASELINE_PLAN = "plan_activity_baseline_plan"
private const val KEY_BASELINE_LEVEL = "plan_activity_baseline_level"

/**
 * The plan proposal screen (US-69, US-68 gate). A proposed plan is always reloaded from the stored row, so a finished
 * stream survives process death. [savedState] only remembers that a request was in flight: a recreated model with no
 * stored plan shows [PlanFailureKind.Interrupted] and never asks (and bills) again by itself.
 */
// One small handler per event and per outcome.
@Suppress("TooManyFunctions")
class PlanProposalViewModel(
    private val plans: CoachPlanRepository,
    private val coach: CoachRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val intakeRepo: CoachIntakeRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlanProposalUiState>(PlanProposalUiState.Loading)
    val uiState: StateFlow<PlanProposalUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<PlanProposalEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private val cardioEdit = CardioEditState(savedState)

    /** The years sheet of an unconfirmed experience (R-55); saving re-reads the list. Absent without a repo. */
    private val rowEditor = intakeRepo?.let { repo ->
        IntakeRowEditor(repo, viewModelScope, savedState) { saved ->
            _uiState.update { state ->
                if (state is PlanProposalUiState.Proposal) state.copy(intake = IntakeSummary.of(saved)) else state
            }
            refresh()
        }
    }
    val editor: StateFlow<IntakeRowEditorState> = rowEditor?.state ?: MutableStateFlow(IntakeRowEditorState())

    fun onEditorEvent(event: IntakeRowEvent) {
        rowEditor?.onEvent(event)
    }

    // Set by the navigation route (PlanProposalRoute.replacingPlanId); null when opening a plan normally.
    private val replacingPlanId: String? = savedState.get<String>(KEY_REPLACING)

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        val wasRequesting = savedState.get<Boolean>(KEY_REQUESTING) == true
        viewModelScope.launch {
            val missing = if (wasRequesting) {
                savedState.remove<Boolean>(KEY_REQUESTING)
                PlanProposalUiState.Failed(PlanFailureKind.Interrupted)
            } else {
                PlanProposalUiState.Idle
            }
            val stored = plans.currentPlan()
            val proposed = stored.proposedToShow()
            when {
                stored.isFailure -> _uiState.value = PlanProposalUiState.Failed(kindOf(stored.exceptionOrNull()))
                proposed != null -> showProposal(proposed)
                else -> _uiState.value = missing
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // A flat dispatcher: one branch per event.
    fun onEvent(event: PlanProposalEvent) {
        when (event) {
            PlanProposalEvent.Request -> if (_uiState.value == PlanProposalUiState.Idle) request()
            PlanProposalEvent.Retry -> if (_uiState.value is PlanProposalUiState.Failed) retry()
            PlanProposalEvent.Accept -> accept()
            PlanProposalEvent.Discard -> discard()
            PlanProposalEvent.OpenCardioEdit -> openCardioEdit()
            is PlanProposalEvent.CardioMinutesChanged -> changeCardio(event.minutes)
            PlanProposalEvent.SaveCardio -> saveCardio()
            PlanProposalEvent.DismissCardioEdit -> dismissCardioEdit()
            is PlanProposalEvent.CutRatePicked -> updateOptions(event.pct, null)
            is PlanProposalEvent.DietBreakToggled -> updateOptions(null, event.on)
            PlanProposalEvent.Refresh -> refresh()
            PlanProposalEvent.ConfirmValues -> confirmValues()
            PlanProposalEvent.OpenActivitySheet -> openActivitySheet()
            is PlanProposalEvent.PickActivityLevel -> pickActivityLevel(event.level)
            PlanProposalEvent.CloseActivitySheet -> setActivityDraft(null)
        }
    }

    private fun restoredActivityDraft(): ActivityLevel? = savedState.get<String>(KEY_ACTIVITY_DRAFT)
        ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }

    private fun setActivityDraft(level: ActivityLevel?) {
        if (level == null) {
            savedState.remove<String>(KEY_ACTIVITY_DRAFT)
        } else {
            savedState[KEY_ACTIVITY_DRAFT] = level.name
        }
        _uiState.update { state ->
            if (state is PlanProposalUiState.Proposal &&
                !state.savingLevel
            ) {
                state.copy(activitySheet = level)
            } else {
                state
            }
        }
    }

    private fun openActivitySheet() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.busy) return
        val levelRow = proposal.unconfirmedValues.firstOrNull { it.key == PlanInputKey.ActivityLevel }
        val text = (levelRow?.value as? PlanInputValue.Text)?.text?.filter { it.isLetter() }?.lowercase()
        val current = ActivityLevel.entries.firstOrNull { it.name.lowercase() == text }
        setActivityDraft(proposal.activitySheet ?: current ?: ActivityLevel.Moderate)
    }

    /** Confirms the values with the picked level (no plan is requested); a different level makes the plan stale. */
    private fun pickActivityLevel(level: ActivityLevel) {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.activitySheet == null || proposal.busy) return
        _uiState.value = proposal.copy(savingLevel = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), level)
            if (result.isSuccess) {
                savedState.remove<String>(KEY_ACTIVITY_DRAFT)
                val list = coach.planInputs(PlanInputKind.Plan, today()).getOrNull()
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal) {
                        state.copy(
                            unconfirmedValues = list?.blockingRows() ?: state.unconfirmedValues,
                            stale = list?.let { isStale(state.plan.id, it) } ?: state.stale,
                            activitySheet = null,
                            savingLevel = false,
                        )
                    } else {
                        state
                    }
                }
                effectChannel.send(PlanProposalEffect.ValuesConfirmed)
            } else {
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal) state.copy(savingLevel = false) else state
                }
                effectChannel.send(failed(PlanAction.Confirm, result.appErrorOrNull()))
            }
        }
    }

    /** Stores the cut options and replaces the plan with the recomputed one; the previous plan stays on failure. */
    @Suppress("ReturnCount") // Guards: state, cut phase, valid rate and an in-flight call.
    private fun updateOptions(ratePct: Double?, dietBreak: Boolean?) {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (!proposal.hasCutOptions || proposal.busy) return
        val current = proposal.plan.nutrition.phaseOptions
        val rate = ratePct ?: current.cutRatePct
        if (!CutRate.isValid(rate)) return
        _uiState.value = proposal.copy(optionsSaving = true)
        viewModelScope.launch {
            val result = plans.updatePlanOptions(proposal.plan.id, today(), rate, dietBreak ?: current.dietBreak)
            val updated = result.getOrNull()
            _uiState.update { state ->
                if (state is PlanProposalUiState.Proposal) {
                    state.copy(plan = updated ?: state.plan, optionsSaving = false)
                } else {
                    state
                }
            }
            if (updated == null) effectChannel.send(failed(PlanAction.Options, result.appErrorOrNull()))
        }
    }

    @Suppress("ReturnCount") // Guards for the state, a plan without activity and an in-flight call.
    private fun openCardioEdit() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        val saved = proposal.plan.nutrition.activity?.cardioMinWeek ?: return
        if (proposal.busy) return
        cardioEdit.open(saved)
        _uiState.value = proposal.copy(cardioEdit = cardioEdit.current(saved))
    }

    private fun changeCardio(minutes: Int) {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        val edit = proposal.cardioEdit?.takeUnless { it.saving } ?: return
        cardioEdit.change(minutes)
        _uiState.value = proposal.copy(cardioEdit = cardioEdit.current(edit.saved))
    }

    private fun dismissCardioEdit() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.cardioEdit?.saving != false) return
        cardioEdit.close()
        _uiState.value = proposal.copy(cardioEdit = null)
    }

    private fun saveCardio() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        val edit = proposal.cardioEdit?.takeIf { it.canSave } ?: return
        _uiState.value = proposal.copy(cardioEdit = edit.copy(saving = true))
        viewModelScope.launch {
            val result = plans.setCardioMinutes(proposal.plan.id, edit.draft)
            val updated = result.getOrNull()
            if (updated != null) {
                cardioEdit.close()
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal) {
                        state.copy(plan = updated, cardioEdit = null)
                    } else {
                        state
                    }
                }
                effectChannel.send(PlanProposalEffect.CardioSaved)
            } else {
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal) {
                        state.copy(cardioEdit = cardioEdit.current(edit.saved))
                    } else {
                        state
                    }
                }
                effectChannel.send(failed(PlanAction.Cardio, result.appErrorOrNull()))
            }
        }
    }

    private fun request() {
        viewModelScope.launch { runRequest() }
    }

    private suspend fun runRequest() {
        _uiState.value = PlanProposalUiState.Requesting
        savedState[KEY_REQUESTING] = true
        val result = plans.requestPlan(today())
        savedState.remove<Boolean>(KEY_REQUESTING)
        result.fold(
            onSuccess = { showProposal(it) },
            onFailure = { _uiState.value = PlanProposalUiState.Failed(kindOf(it)) },
        )
    }

    private fun retry() {
        _uiState.value = PlanProposalUiState.Loading
        viewModelScope.launch {
            val proposed = plans.currentPlan().proposedToShow()
            if (proposed != null) showProposal(proposed) else runRequest()
        }
    }

    /**
     * The stored proposed plan, except the one the user is replacing (it must not be re-shown nor re-billed) and a
     * goal-switch proposal, which belongs to "Bloque nuevo" and has no accept/discard card.
     */
    private fun Result<CoachPlan?>.proposedToShow(): CoachPlan? = getOrNull()
        ?.takeIf { it.status == PlanStatus.Proposed && it.id != replacingPlanId && !it.fromGoalSwitch }

    private suspend fun showProposal(plan: CoachPlan) {
        _uiState.value = coach.planInputs(PlanInputKind.Plan, today()).fold(
            onSuccess = {
                val saved = plan.nutrition.activity?.cardioMinWeek
                PlanProposalUiState.Proposal(
                    plan,
                    unconfirmedValues = it.blockingRows(),
                    stale = isStale(plan.id, it),
                    cardioEdit = cardioEdit.current(saved),
                    activitySheet = restoredActivityDraft(),
                    intake = readIntake() ?: IntakeSummary(),
                )
            },
            onFailure = { PlanProposalUiState.Failed(kindOf(it)) },
        )
    }

    /** The stated intake, or null without a repo or when it cannot be read (the sheets then open empty). */
    private suspend fun readIntake(): IntakeSummary? = intakeRepo?.get()?.getOrNull()?.let { IntakeSummary.of(it) }

    /** The inputs the server refuses the plan for (`blocksAccept`) that are still provisional. */
    private fun PlanInputs.blockingRows(): List<PlanInputRow> =
        inputs.filter { it.blocksAccept && it.status == PlanInputStatus.Provisional }.map { it.toRow() }

    /**
     * The plan does not store the activity level it used, so the level seen when the proposal was first shown is the
     * baseline (kept in [savedState]); a different level later means the plan no longer matches the user's data.
     */
    @Suppress("ReturnCount") // Guards: no level in the list, a new plan.
    private fun isStale(planId: String, list: PlanInputs): Boolean {
        val level = (list.inputs.firstOrNull { it.key == PlanInputKey.ActivityLevel }?.value as? PlanInputValue.Text)
            ?.text ?: return false
        if (savedState.get<String>(KEY_BASELINE_PLAN) != planId) {
            savedState[KEY_BASELINE_PLAN] = planId
            savedState[KEY_BASELINE_LEVEL] = level
            return false
        }
        return savedState.get<String>(KEY_BASELINE_LEVEL) != level
    }

    /** Re-reads the list after the user returned from an editor; a failed read leaves the screen as it is. */
    private fun refresh() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.busy) return
        viewModelScope.launch {
            val intake = readIntake()
            coach.planInputs(PlanInputKind.Plan, today()).onSuccess { list ->
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal && !state.busy) {
                        state.copy(
                            intake = intake ?: state.intake,
                            unconfirmedValues = list.blockingRows(),
                            stale = isStale(state.plan.id, list),
                            dataChanged = false,
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Confirms the estimates as shown (the footer call of "Antes de empezar"), then re-reads. */
    private fun confirmValues() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.busy) return
        _uiState.value = proposal.copy(confirming = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), null)
            if (result.isSuccess) {
                val list = coach.planInputs(PlanInputKind.Plan, today()).getOrNull()
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal) {
                        state.copy(
                            unconfirmedValues = list?.blockingRows() ?: state.unconfirmedValues,
                            stale = list?.let { isStale(state.plan.id, it) } ?: state.stale,
                            dataChanged = list == null && state.dataChanged,
                            confirming = false,
                        )
                    } else {
                        state
                    }
                }
                effectChannel.send(PlanProposalEffect.ValuesConfirmed)
            } else {
                _uiState.update { state ->
                    if (state is PlanProposalUiState.Proposal) state.copy(confirming = false) else state
                }
                effectChannel.send(failed(PlanAction.Confirm, result.appErrorOrNull()))
            }
        }
    }

    private fun accept() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (!proposal.canAccept) return
        _uiState.value = proposal.copy(accepting = true)
        viewModelScope.launch {
            val result = plans.acceptPlan(proposal.plan.id, today())
            val error = result.exceptionOrNull()
            when {
                result.isSuccess -> {
                    _uiState.value = PlanProposalUiState.Closed
                    effectChannel.send(PlanProposalEffect.Accepted)
                }

                (error as? CoachException)?.failure == CoachFailure.NeedsConfirmation -> {
                    // The server's check is the truth: show its values and ask for a new confirmation.
                    // A failed re-read still blocks: the server said the data changed.
                    val list = coach.planInputs(PlanInputKind.Plan, today()).getOrNull()
                    _uiState.value = proposal.copy(
                        unconfirmedValues = list?.blockingRows() ?: proposal.unconfirmedValues,
                        accepting = false,
                        dataChanged = true,
                    )
                }

                else -> {
                    _uiState.value = proposal.copy(accepting = false)
                    effectChannel.send(failed(PlanAction.Accept, result.appErrorOrNull()))
                }
            }
        }
    }

    private fun discard() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.busy) return
        _uiState.value = proposal.copy(discarding = true)
        viewModelScope.launch {
            val result = plans.discardPlan(proposal.plan.id)
            if (result.isSuccess) {
                _uiState.value = PlanProposalUiState.Closed
                effectChannel.send(PlanProposalEffect.Discarded)
            } else {
                _uiState.value = proposal.copy(discarding = false)
                effectChannel.send(failed(PlanAction.Discard, result.appErrorOrNull()))
            }
        }
    }

    private fun failed(action: PlanAction, error: AppError?) =
        PlanProposalEffect.ActionFailed(action, offline = error == AppError.Network)

    private fun kindOf(failure: Throwable?): PlanFailureKind = planFailureKindOf(failure)
}
