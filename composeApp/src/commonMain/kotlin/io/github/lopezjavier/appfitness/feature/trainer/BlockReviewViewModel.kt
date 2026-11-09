package io.github.lopezjavier.appfitness.feature.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.lopezjavier.appfitness.app.ReminderCoordinator
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.appErrorOrNull
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.usecase.DecideCalorieProposalUseCase
import io.github.lopezjavier.appfitness.domain.usecase.DecidePhaseChangeUseCase
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import io.github.lopezjavier.appfitness.domain.usecase.LoadBlockReviewUseCase
import io.github.lopezjavier.appfitness.domain.usecase.MarkBlockReviewSeen
import io.github.lopezjavier.appfitness.domain.usecase.PhaseChangeResult
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

private const val KEY_NEXT_PLAN_ID = "block_review_next_plan_id"
private const val KEY_MARKED = "block_review_marked"

/**
 * "Revisión del bloque" (US-101, US-103): the read-only review of the block that ended, its phase proposal and a
 * pending calorie proposal. Decisions go through the T-233 use cases; after one is stored the review is read again
 * so the shown decision is the server's. The id of the plan "Aceptar" builds survives recreation in [savedState]; an
 * in-flight decision does not, because the screen re-reads the stored decision. Nothing here calls the coach model.
 */
@Suppress("LongParameterList") // One port per use case, as the other trainer view models.
class BlockReviewViewModel(
    private val loadReview: LoadBlockReviewUseCase,
    private val decidePhase: DecidePhaseChangeUseCase,
    private val plans: CoachPlanRepository,
    private val decideCalorie: DecideCalorieProposalUseCase,
    private val markSeen: MarkBlockReviewSeen,
    private val reminders: ReminderCoordinator,
    private val names: ExerciseNameResolver,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<BlockReviewUiState>(BlockReviewUiState.Loading)
    val uiState: StateFlow<BlockReviewUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<BlockReviewEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: BlockReviewEvent) {
        when (event) {
            BlockReviewEvent.Retry -> load()
            is BlockReviewEvent.DecidePhase -> decide(event.decision)
            BlockReviewEvent.RetryDecision -> content()?.decisionFailed?.let(::decide)
            BlockReviewEvent.ApplyCalorie -> decideCalorieProposal(CalorieChoice.Apply)
            BlockReviewEvent.NotNowCalorie -> decideCalorieProposal(CalorieChoice.NotNow)
        }
    }

    // The block boundaries are UTC dates, like the rest of the coach SQL.
    private fun today(): LocalDate = clock.now().toLocalDateTime(TimeZone.UTC).date

    private fun content(): BlockReviewUiState.Content? = _uiState.value as? BlockReviewUiState.Content

    private fun load() {
        _uiState.value = BlockReviewUiState.Loading
        viewModelScope.launch {
            val state = fetch()
            _uiState.value = state
            if (state is BlockReviewUiState.Content) markAsSeen(state.review)
        }
    }

    // Opening the stored review clears its block-end notice: once per open, kept in [savedState]; a failure is silent.
    private suspend fun markAsSeen(review: BlockReview) {
        val id = review.id ?: return // The early review is not stored.
        if (review.early || review.seen || savedState.get<String>(KEY_MARKED) == id) return
        if (markSeen(id).isSuccess) {
            savedState[KEY_MARKED] = id
            reminders.refresh()
        }
    }

    private suspend fun fetch(): BlockReviewUiState {
        val result = loadReview(today())
        val review = result.getOrNull()
        return when {
            result.isFailure -> if (result.appErrorOrNull() == AppError.Network) {
                BlockReviewUiState.Offline
            } else {
                BlockReviewUiState.Error
            }

            review == null -> BlockReviewUiState.Empty

            else -> names.blockReviewContent(
                review = review,
                // A failed read means no calorie card.
                calorieProposal = plans.pendingCalorieProposal(today()).getOrNull(),
                nextPlanId = savedState.get<String>(KEY_NEXT_PLAN_ID),
            )
        }
    }

    private fun decide(decision: PhaseDecision) {
        val current = content()
        val reviewId = current?.review?.id // The early review is not stored: nothing to decide.
        if (current == null || reviewId == null || !current.canDecide()) return
        _uiState.value = current.copy(deciding = decision, decisionFailed = null)
        viewModelScope.launch {
            when (val result = decidePhase(reviewId, decision, today())) {
                is PhaseChangeResult.Done -> {
                    result.nextPlanId?.let { savedState[KEY_NEXT_PLAN_ID] = it }
                    // The stored decision comes from the server; if that read fails, the choice just made stands.
                    val fresh = loadReview(today()).getOrNull() ?: current.review.copy(phaseDecision = decision)
                    _uiState.update { state ->
                        (state as? BlockReviewUiState.Content)?.copy(
                            review = fresh,
                            deciding = null,
                            nextPlanId = result.nextPlanId ?: state.nextPlanId,
                        ) ?: state
                    }
                    effectChannel.send(BlockReviewEffect.PhaseDecided(decision))
                }

                is PhaseChangeResult.Failed -> {
                    _uiState.update { state ->
                        (state as? BlockReviewUiState.Content)?.copy(deciding = null, decisionFailed = decision)
                            ?: state
                    }
                    effectChannel.send(BlockReviewEffect.PhaseDecisionFailed)
                }
            }
        }
    }

    @Suppress("ReturnCount") // Guards: not showing content, no proposal, or a decision in flight.
    private fun decideCalorieProposal(choice: CalorieChoice) {
        val current = content() ?: return
        val proposal = current.calorieProposal ?: return
        if (current.decidingCalorie) return
        _uiState.value = current.copy(decidingCalorie = true, calorieFailed = null)
        viewModelScope.launch {
            val result = when (choice) {
                CalorieChoice.Apply -> decideCalorie.apply(proposal.id, today())
                CalorieChoice.NotNow -> decideCalorie.notNow(proposal.id, today())
            }
            _uiState.update { state ->
                val loaded = state as? BlockReviewUiState.Content ?: return@update state
                if (result.isSuccess) {
                    loaded.copy(calorieProposal = null, decidingCalorie = false)
                } else {
                    loaded.copy(decidingCalorie = false, calorieFailed = choice)
                }
            }
            effectChannel.send(
                if (result.isSuccess) BlockReviewEffect.CalorieDecided(choice) else BlockReviewEffect.CalorieFailed,
            )
        }
    }
}

private fun BlockReviewUiState.Content.canDecide() = deciding == null && phaseDecidable
