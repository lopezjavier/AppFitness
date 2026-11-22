package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockProgress
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockWeightProgress
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.LiftSeries
import io.github.lopezjavier.appfitness.domain.model.LiftStatus
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.ProgressState

private const val BASE_WEEK = 1

// Gaps that can be fixed come before the informational "no longer recorded" ones.
private val MISSING_ORDER =
    listOf(MissingKind.WeighIns, MissingKind.Food, MissingKind.DeletedSessions, MissingKind.DeletedWeighIns)

/**
 * The coach card of the block review (`07` §3 (A0)). [progress] must already be filtered to the exercises in [names]
 * (R-54); it is null for a v1 review (no sentence, the action comes from the kept fields). The first action that
 * applies wins: on a live review a decidable phase proposal, then a pending calorie proposal; then a named stall, then
 * missing data, then "create the next block". A stored review ([live] false) reads a decided phase as text first and
 * never offers an undecided proposal.
 */
fun blockReportHeadline(
    review: BlockReview,
    progress: BlockProgress?,
    names: Map<String, String>,
    calorieProposal: CalorieProposal?,
    live: Boolean,
): BlockCoachUi = BlockCoachUi(sentenceOf(progress, names), actionOf(review, progress, names, calorieProposal, live))

private fun actionOf(
    review: BlockReview,
    progress: BlockProgress?,
    names: Map<String, String>,
    calorieProposal: CalorieProposal?,
    live: Boolean,
): BlockReportAction {
    val proposal = review.phaseProposal
    val decision = review.phaseDecision
    val missing = MISSING_ORDER.firstOrNull { it in progress?.missing.orEmpty() }
    return when {
        live && proposal != null && decision == null -> BlockReportAction.PendingPhase(proposal.toPhase)
        live && calorieProposal != null -> BlockReportAction.PendingCalorie(calorieProposal)
        !live && proposal != null && decision != null -> BlockReportAction.DecidedPhase(decision, proposal.toPhase)
        review.stalls.any { it.exerciseId in names } -> BlockReportAction.Stall
        missing != null -> BlockReportAction.MissingData(missing, past = !live)
        else -> BlockReportAction.CreateNext
    }
}

/** The block weight change, null unless both the first and the last plan week have 3 weigh-in days. */
internal fun gatedChangeKg(weight: BlockWeightProgress): Double? =
    weight.changeKg.takeIf { weight.daysFirst >= WEIGH_IN_GATE && weight.daysLast >= WEIGH_IN_GATE }

private fun sentenceOf(progress: BlockProgress?, names: Map<String, String>): BlockSentenceUi? {
    if (progress == null) return null
    val exercises = progress.strength.groups.flatMap { it.exercises }.filter { it.exerciseId in names }
    val ups = if (progress.strength.blockWeek <= BASE_WEEK) null else exercises.count { it.state == ProgressState.Up }
    // Best percentage first, then the id, as the server picks it (`headline.main_lift`).
    val main = progress.lifts
        .filter { it.exerciseId in names && it.state == LiftStatus.Up && it.changeKg != null }
        .sortedWith(
            compareByDescending<LiftSeries> { it.changePct ?: 0.0 }
                .thenBy { it.exerciseId },
        )
        .firstOrNull()
    return BlockSentenceUi(
        ups = ups,
        mainLift = main?.let { lift -> lift.changeKg?.let { BlockMainLiftUi(lift.exerciseId, it, lift.changePct) } },
        weightChangeKg = progress.weight?.let(::gatedChangeKg),
    )
}
