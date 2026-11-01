package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachWeeksRepository
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

/** The stored block of an open plan; a failed read means none, like the plan entry itself. */
private suspend fun CoachBlockRepository?.summaryFor(plan: PlanSummary): BlockSummary {
    val planId = when (plan) {
        is PlanSummary.Proposed -> plan.plan.id
        is PlanSummary.Accepted -> plan.plan.id
        PlanSummary.None -> null
    }
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
    savedState: SavedStateHandle = SavedStateHandle(),
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

    fun onEvent(event: TrainerEvent) {
        when (event) {
            TrainerEvent.Load -> load()
            TrainerEvent.Refresh -> refresh()
            is TrainerEvent.DecideCalorieChange -> decideCalorieChange(event.accept)
            is TrainerEvent.DecideDeload -> decideDeload(event.accept)
            TrainerEvent.OpenCardioEdit -> openCardioEdit()
            is TrainerEvent.CardioMinutesChanged -> changeCardio(event.minutes)
            TrainerEvent.SaveCardio -> saveCardio()
            TrainerEvent.DismissCardioEdit -> dismissCardioEdit()
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
                        cardioEdit = state.cardioEdit,
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
        val change = if (plan is PlanSummary.Accepted) plans?.pendingCalorieChange(today())?.getOrNull() else null
        val block = blocks.summaryFor(plan)
        // Weeks and the deload card need an accepted plan with a saved block; failed reads mean none.
        val savedPlanId = (plan as? PlanSummary.Accepted)?.plan?.id?.takeIf { block is BlockSummary.Saved }
        val deload = savedPlanId?.let { coachWeeks?.pendingDeload(today())?.getOrNull() }
        val planWeeks = savedPlanId?.let { coachWeeks?.weeks(it, today())?.getOrNull() }.orEmpty()
        return TrainerUiState.Content(
            plan = plan,
            calorieChange = change,
            block = block,
            weeks = planWeeks,
            deload = deload,
            provisionalCount = provisional,
            cardioEdit = cardioEdit.current((plan as? PlanSummary.Accepted)?.plan?.cardioMinutes()),
        )
    }

    private suspend fun readPlan(): PlanSummary {
        val plan = plans?.currentPlan()?.getOrNull() ?: return PlanSummary.None
        return if (plan.status == PlanStatus.Accepted) PlanSummary.Accepted(plan) else PlanSummary.Proposed(plan)
    }

    @Suppress("ReturnCount") // Guards for a missing card, repository or an in-flight decision.
    private fun decideCalorieChange(accept: Boolean) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        val change = content.calorieChange ?: return
        val repository = plans ?: return
        if (content.decidingChange) return
        _uiState.value = content.copy(decidingChange = true)
        viewModelScope.launch {
            val result = repository.decideCalorieChange(change.id, accept, today())
            if (result.isSuccess) {
                // Accepting changes the plan's calories: reload the summary.
                val plan = readPlan()
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        val kept = if (plan is PlanSummary.None) state.plan else plan
                        state.copy(calorieChange = null, decidingChange = false, plan = kept)
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.CalorieChangeDecided(accept))
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) state.copy(decidingChange = false) else state
                }
                effectChannel.send(
                    TrainerEffect.CalorieChangeFailed(accept, offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }
}
