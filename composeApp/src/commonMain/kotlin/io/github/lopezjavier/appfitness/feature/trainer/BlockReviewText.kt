package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_lift
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_lift_now
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_not_reached
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_reached
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_weight
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_outcome_weight_now
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_back_to_goal
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_cut_length
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_floor_limited
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_reason_floor_limited_short
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_trend
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_trend_none
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_waist_margin
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_no_target
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_none
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_weight_range
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockWaist
import io.github.lopezjavier.appfitness.domain.model.BlockWeight
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
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

    enum class OutcomeTone { Success, Neutral }

    /** "Conseguido" / "No conseguido": informational only (R-41), never colour alone. */
    data class OutcomeLabel(val res: StringResource, val tone: OutcomeTone)

    /** "{first} → {last} kg ({±change})", or "Sin registros" when the lift was never logged. */
    fun trend(lift: BlockLift): Line {
        val first = lift.firstE1rm
        val last = lift.lastE1rm
        if (first == null || last == null) return Line(Res.string.block_review_trend_none)
        val change = lift.changeKg ?: (last - first)
        return Line(
            Res.string.block_review_trend,
            listOf(WeekSummaryText.kg(first), WeekSummaryText.kg(last), WeekSummaryText.signedKg(change)),
        )
    }

    /** Average weight over the block against the target rate; whatever the trend lacks is left out. */
    @Suppress("ReturnCount") // One early return per missing figure.
    fun weight(weight: BlockWeight?): Line {
        val first = weight?.firstAvgKg
        val last = weight?.lastAvgKg
        if (weight == null || first == null || last == null) return Line(Res.string.block_review_weight_none)
        val range = listOf(WeekSummaryText.kg(first), WeekSummaryText.kg(last))
        val slope = weight.slopeKgWeek ?: return Line(Res.string.block_review_weight_range, range)
        val withSlope = range + WeekSummaryText.signedKg(slope)
        val target = weight.targetRateKgWeek ?: return Line(Res.string.block_review_weight_no_target, withSlope)
        return Line(Res.string.block_review_weight, withSlope + WeekSummaryText.signedKg(target))
    }

    fun outcomeLabel(outcome: BlockOutcome): OutcomeLabel = if (outcome.reached) {
        OutcomeLabel(Res.string.block_review_outcome_reached, OutcomeTone.Success)
    } else {
        OutcomeLabel(Res.string.block_review_outcome_not_reached, OutcomeTone.Neutral)
    }

    /**
     * The target of an outcome with where it stands now. [liftName] is the resolved exercise name of an e1RM outcome
     * (R-54); without it, or without a target value, the line is omitted.
     */
    fun outcomeLine(outcome: BlockOutcome, liftName: String?): Line? {
        val target = outcome.targetValue?.let { WeekSummaryText.kg(it) } ?: return null
        val now = outcome.currentValue?.let { WeekSummaryText.kg(it) }
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

    /** "Cintura: −2,0 cm desde el 24 oct", with the measurement-margin note when the change is below it. */
    fun waist(waist: BlockWaist): Line = Line(
        if (waist.withinMargin) Res.string.block_review_waist_margin else Res.string.block_review_waist,
        listOf(signedTenths(waist.deltaCm), AppDateFormat.shortDate(waist.from)),
    )

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
