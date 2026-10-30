package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val KEY_REQUESTING = "plan_requesting"

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
) : ViewModel() {
    private val _uiState = MutableStateFlow<PlanProposalUiState>(PlanProposalUiState.Loading)
    val uiState: StateFlow<PlanProposalUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<PlanProposalEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

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
            val proposed = stored.getOrNull()?.takeIf { it.status == PlanStatus.Proposed }
            when {
                stored.isFailure -> _uiState.value = PlanProposalUiState.Failed(kindOf(stored.exceptionOrNull()))
                proposed != null -> showProposal(proposed)
                else -> _uiState.value = missing
            }
        }
    }

    fun onEvent(event: PlanProposalEvent) {
        when (event) {
            PlanProposalEvent.Request -> if (_uiState.value == PlanProposalUiState.Idle) request()
            PlanProposalEvent.Retry -> if (_uiState.value is PlanProposalUiState.Failed) retry()
            PlanProposalEvent.Confirm -> confirm()
            PlanProposalEvent.Accept -> accept()
            PlanProposalEvent.Discard -> discard()
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
            val stored = plans.currentPlan()
            val proposed = stored.getOrNull()?.takeIf { it.status == PlanStatus.Proposed }
            if (proposed != null) showProposal(proposed) else runRequest()
        }
    }

    private suspend fun showProposal(plan: CoachPlan) {
        _uiState.value = coach.derivedValues(today()).fold(
            onSuccess = { PlanProposalUiState.Proposal(plan, it) },
            onFailure = { PlanProposalUiState.Failed(kindOf(it)) },
        )
    }

    private fun confirm() {
        val proposal = _uiState.value as? PlanProposalUiState.Proposal ?: return
        if (proposal.busy) return
        _uiState.value = proposal.copy(confirming = true)
        viewModelScope.launch {
            val result = coach.confirm(today(), null)
            val values = result.getOrNull()
            if (values != null) {
                _uiState.value = proposal.copy(values = values, confirming = false, dataChanged = false)
                effectChannel.send(PlanProposalEffect.ValuesConfirmed)
            } else {
                _uiState.value = proposal.copy(confirming = false)
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
                    val fresh = coach.derivedValues(today()).getOrNull() ?: proposal.values
                    _uiState.value = proposal.copy(values = fresh, accepting = false, dataChanged = true)
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
