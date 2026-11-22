package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_lift
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_lift_now
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_weight
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_weight_now
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_back_to_goal
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_cut_length
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_floor_limited
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_floor_limited_short
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CUT_LENGTH_FALLBACK_WEEKS = 12
private const val KEY_DEFICIT_WEEKS = "deficit_weeks"
private const val KEY_SLOPE = "slope_kg_week"
private const val KEY_TARGET_RATE = "target_rate_kg_week"

/**
 * The figures of the end-of-block review as the copy shows them (US-101, US-103): each function returns the string
 * resource with its formatted arguments, so it stays pure and unit-testable. Numbers reuse [WeekSummaryText].
 */
internal object BlockReviewText {
    /** A string resource with its already formatted arguments. */
    data class Line(val res: StringResource, val args: List<String> = emptyList())

    /**
     * The target of an objective with where it stands now ("Peso: objetivo 78 kg · ahora 79,8 kg"). [liftName] is the
     * resolved exercise name of an e1RM objective (R-54); without it the line is omitted.
     */
    fun outcomeLine(outcome: OutcomeUi, liftName: String?): Line? {
        val target = WeekSummaryText.kg(outcome.targetKg)
        val now = outcome.nowKg?.let { WeekSummaryText.kg(it) }
        return when (outcome.kind) {
            OutcomeKind.Weight -> if (now == null) {
                Line(Res.string.block_review_outcome_weight, listOf(target))
            } else {
                Line(Res.string.block_review_outcome_weight_now, listOf(target, now))
            }

            OutcomeKind.E1rm -> when {
                liftName == null -> null
                now == null -> Line(Res.string.block_review_outcome_lift, listOf(liftName, target))
                else -> Line(Res.string.block_review_outcome_lift_now, listOf(liftName, target, now))
            }
        }
    }

    /** The one-sentence reason of the phase proposal, from the server's own figures (07 section 12.3). */
    fun proposalReason(proposal: PhaseProposal): Line = when (proposal.reason) {
        PhaseChangeReason.CutLength -> Line(
            Res.string.block_review_reason_cut_length,
            listOf((proposal.figures[KEY_DEFICIT_WEEKS]?.roundToInt() ?: CUT_LENGTH_FALLBACK_WEEKS).toString()),
        )

        PhaseChangeReason.FloorLimited -> {
            val slope = proposal.figures[KEY_SLOPE]
            val target = proposal.figures[KEY_TARGET_RATE]
            if (slope == null || slope >= 0.0 || target == null) {
                Line(Res.string.block_review_reason_floor_limited_short)
            } else {
                Line(
                    Res.string.block_review_reason_floor_limited,
                    listOf(WeekSummaryText.kg(abs(slope)), WeekSummaryText.kg(abs(target))),
                )
            }
        }

        PhaseChangeReason.BackToGoal -> Line(Res.string.block_review_reason_back_to_goal)
    }
}
