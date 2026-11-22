package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.usecase.ExerciseNameResolver
import kotlinx.datetime.LocalDate

/**
 * The shared builders of the read-only report content, used by the live screens and by the stored reports (US-129) so
 * both show the same model. A failed name read leaves the name-dependent rows out (R-54) instead of failing the report.
 */
internal suspend fun ExerciseNameResolver.namesById(): Map<String, String> = resolve().getOrNull()?.byId.orEmpty()

internal suspend fun ExerciseNameResolver.weekSummaryContent(summary: WeekSummary) =
    WeekSummaryUiState.Content(summary, namesById())

/**
 * The block review content with its progress-first [BlockReviewUiState.Content.report]. [live] is true on the live
 * review and false on a stored one; [today] decides the objective chips.
 */
internal suspend fun ExerciseNameResolver.blockReviewContent(
    review: BlockReview,
    calorieProposal: CalorieProposal? = null,
    nextPlanId: String? = null,
    live: Boolean,
    today: LocalDate,
) = BlockReviewUiState.Content(
    review = review,
    exerciseNames = namesById(),
    calorieProposal = calorieProposal,
    nextPlanId = nextPlanId,
).withReport(live, today)

/** Rebuilds the report; call it after every change of `review` or `calorieProposal` (the action depends on them). */
internal fun BlockReviewUiState.Content.withReport(live: Boolean, today: LocalDate) =
    copy(report = blockReportOf(review, exerciseNames, calorieProposal, live, today))
