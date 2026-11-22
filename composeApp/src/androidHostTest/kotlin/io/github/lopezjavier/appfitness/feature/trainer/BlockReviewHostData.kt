package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockProgress
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.WaistProgress
import kotlinx.datetime.LocalDate

/** The day the host tests open the review: after the 15 nov deadline of the objectives below. */
internal val reviewToday = LocalDate(2026, 11, 22)

internal val deadlinePassed = LocalDate(2026, 11, 15)
internal val deadlineAhead = LocalDate(2027, 2, 15)

internal fun weightOutcome(deadline: LocalDate?, reached: Boolean?) =
    BlockOutcome(OutcomeKind.Weight, "weight", 78.0, "kg", deadline, 79.8, reached)

internal fun benchOutcome(deadline: LocalDate?, reached: Boolean?) =
    BlockOutcome(OutcomeKind.E1rm, "id-bench", 90.0, "kg", deadline, 84.0, reached)

internal fun waistProgress() = WaistProgress(
    cm = 86.0,
    date = LocalDate(2026, 10, 17),
    refCm = 88.0,
    refDate = LocalDate(2026, 9, 20),
    changeCm = -2.0,
)

/** A review of a cut block with real progress (schema v2); override [progress] for another shape. */
internal fun v2Review(progress: BlockProgress? = blockProgress(), review: BlockReview = blockReview()) =
    review.copy(progress = progress)

/** The content of the live review ([live]) or of the stored one, built by the real mapper (as the ViewModels do). */
internal fun reviewContent(
    review: BlockReview = v2Review(),
    live: Boolean = true,
    withCalorie: Boolean = false,
    nextPlanId: String? = null,
    deciding: PhaseDecision? = null,
    decisionFailed: PhaseDecision? = null,
    calorieFailed: CalorieChoice? = null,
) = BlockReviewUiState.Content(
    review = review,
    exerciseNames = blockNames,
    calorieProposal = if (withCalorie) calorieProposal() else null,
    deciding = deciding,
    decisionFailed = decisionFailed,
    calorieFailed = calorieFailed,
    nextPlanId = nextPlanId,
).withReport(live, reviewToday)
