package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PlanInputKind
import io.github.lopezjavier.appfitness.domain.model.PlanInputStatus
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
private const val KEY_REPLACING = "replacingPlanId"

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

    fun onEvent(event: PlanProposalEvent) {
        when (event) {
            PlanProposalEvent.Request -> if (_uiState.value == PlanProposalUiState.Idle) request()
            PlanProposalEvent.Retry -> if (_uiState.value is PlanProposalUiState.Failed) retry()
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
            val proposed = plans.currentPlan().proposedToShow()
            if (proposed != null) showProposal(proposed) else runRequest()
        }
    }

    /** The stored proposed plan, except the one the user is replacing (it must not be re-shown nor re-billed). */
    private fun Result<CoachPlan?>.proposedToShow(): CoachPlan? =
        getOrNull()?.takeIf { it.status == PlanStatus.Proposed && it.id != replacingPlanId }

    private suspend fun showProposal(plan: CoachPlan) {
        _uiState.value = unconfirmedInputs().fold(
            onSuccess = { PlanProposalUiState.Proposal(plan, unconfirmed = it) },
            onFailure = { PlanProposalUiState.Failed(kindOf(it)) },
        )
    }

    /** True while some input the plan reads is provisional (the server's check is on the same list). */
    private suspend fun unconfirmedInputs(): Result<Boolean> = coach.planInputs(PlanInputKind.Plan, today())
        .map { list -> list.inputs.any { it.status == PlanInputStatus.Provisional } }

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
                    val unconfirmed = unconfirmedInputs().getOrNull() ?: true
                    _uiState.value = proposal.copy(unconfirmed = unconfirmed, accepting = false, dataChanged = true)
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
