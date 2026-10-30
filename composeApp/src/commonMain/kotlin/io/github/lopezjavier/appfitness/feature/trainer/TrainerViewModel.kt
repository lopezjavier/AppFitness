package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.ActivityLevel
import io.github.lopezjavier.appfitness.domain.model.CoachDerivedValues
import io.github.lopezjavier.appfitness.domain.model.PlanStatus
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachRepository
import io.github.lopezjavier.appfitness.domain.repository.NutritionGoalsRepository
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

/**
 * Trainer tab root (US-68): the derived values with their confirmation state. The open edit sheet and its picked
 * level survive recreation through [savedState]; the loaded values are always re-fetched, never persisted.
 */
class TrainerViewModel(
    private val repository: CoachRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val goals: NutritionGoalsRepository? = null,
    private val plans: CoachPlanRepository? = null,
    private val blocks: CoachBlockRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow<TrainerUiState>(TrainerUiState.Loading)
    val uiState: StateFlow<TrainerUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<TrainerEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    // The server accepts a date within one day of UTC today, so it is the UTC date.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    init {
        load()
    }

    fun onEvent(event: TrainerEvent) {
        when (event) {
            TrainerEvent.Load, TrainerEvent.Retry -> load()
            TrainerEvent.Confirm -> submit(level = null, fromSheet = false)
            TrainerEvent.OpenEdit -> openEdit()
            is TrainerEvent.PickLevel -> pickLevel(event.level)
            TrainerEvent.CloseEdit -> setDraft(null)
            is TrainerEvent.DecideCalorieChange -> decideCalorieChange(event.accept)
        }
    }

    private fun load() {
        _uiState.value = TrainerUiState.Loading
        viewModelScope.launch {
            val result = repository.derivedValues(today())
            // The strategy only colours the trend; an unreadable or missing goal leaves it neutral.
            val strategy = if (result.isSuccess) goals?.getOwn()?.getOrNull()?.strategy else null
            // The plan entry and the calorie card never block the derived values: a failed read means none.
            val plan = if (result.isSuccess) readPlan() else PlanSummary.None
            val change = if (plan is PlanSummary.Accepted) plans?.pendingCalorieChange(today())?.getOrNull() else null
            val block = blocks.summaryFor(plan)
            _uiState.value = result.fold(
                onSuccess = {
                    TrainerUiState.Content(
                        it,
                        editSheet = restoredDraft(),
                        strategy = strategy,
                        plan = plan,
                        calorieChange = change,
                        block = block,
                    )
                },
                onFailure = {
                    if (result.appErrorOrNull() == AppError.Network) TrainerUiState.Offline else TrainerUiState.Error
                },
            )
        }
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

    private fun restoredDraft(): ActivityLevel? = savedState.get<String>(KEY_DRAFT)
        ?.let { name -> ActivityLevel.entries.firstOrNull { it.name == name } }

    private fun openEdit() {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        setDraft(content.editSheet ?: content.values.activityLevel.value ?: ActivityLevel.Moderate)
    }

    private fun pickLevel(level: ActivityLevel) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.editSheet != null) submit(level = level, fromSheet = true)
    }

    private fun setDraft(level: ActivityLevel?) {
        if (level == null) savedState.remove<String>(KEY_DRAFT) else savedState[KEY_DRAFT] = level.name
        _uiState.update { state -> if (state is TrainerUiState.Content) state.copy(editSheet = level) else state }
    }

    private fun submit(level: ActivityLevel?, fromSheet: Boolean) {
        val content = _uiState.value as? TrainerUiState.Content ?: return
        if (content.confirming) return
        _uiState.value = content.copy(confirming = true)
        viewModelScope.launch {
            val result = repository.confirm(today(), level)
            val values: CoachDerivedValues? = result.getOrNull()
            if (values != null) {
                if (fromSheet) savedState.remove<String>(KEY_DRAFT)
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) {
                        state.copy(values = values, confirming = false, editSheet = null)
                    } else {
                        state
                    }
                }
                effectChannel.send(TrainerEffect.Confirmed)
            } else {
                _uiState.update { state ->
                    if (state is TrainerUiState.Content) state.copy(confirming = false) else state
                }
                effectChannel.send(
                    TrainerEffect.ConfirmFailed(fromSheet, offline = result.appErrorOrNull() == AppError.Network),
                )
            }
        }
    }

    private companion object {
        const val KEY_DRAFT = "activity_draft"
    }
}
