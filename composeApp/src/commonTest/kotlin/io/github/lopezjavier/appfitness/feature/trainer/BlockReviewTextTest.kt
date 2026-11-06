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
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockWaist
import io.github.lopezjavier.appfitness.domain.model.BlockWeight
import io.github.lopezjavier.appfitness.domain.model.OutcomeKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockReviewTextTest {
    private fun outcome(kind: OutcomeKind, target: Double?, current: Double?, reached: Boolean) = BlockOutcome(
        kind,
        if (kind ==
            OutcomeKind.Weight
        ) {
            "weight"
        } else {
            "id-bench"
        },
        target,
        null,
        null,
        current,
        reached,
    )

    @Test
    fun trend_showsFirstLastAndSignedChange() {
        val line = BlockReviewText.trend(blockLift(first = 100.0, last = 102.5))

        assertEquals(Res.string.block_review_trend, line.res)
        assertEquals(listOf("100", "102,5", "+2,5"), line.args)
    }

    @Test
    fun trend_withADrop_usesTheRealMinus() {
        assertEquals(listOf("100", "97,5", "−2,5"), BlockReviewText.trend(blockLift(first = 100.0, last = 97.5)).args)
    }

    @Test
    fun trend_withNoChange_isZeroWithoutSign() {
        assertEquals("0", BlockReviewText.trend(blockLift(first = 100.0, last = 100.0)).args.last())
    }

    @Test
    fun trend_withoutRecords_saysSoWithNoArgs() {
        val line = BlockReviewText.trend(blockLift(first = null, last = null))

        assertEquals(Res.string.block_review_trend_none, line.res)
        assertEquals(emptyList(), line.args)
    }

    @Test
    fun deloadReviewWithWeeksOneToThree_usesTheServerFiguresAsGiven() {
        val lift = blockLift(first = 80.0, last = 85.0)

        assertEquals(listOf(1, 3), lift.weeks.map { it.week })
        assertEquals(listOf("80", "85", "+5"), BlockReviewText.trend(lift).args)
    }

    @Test
    fun weight_withEveryFigure_showsRangeSlopeAndTarget() {
        val line = BlockReviewText.weight(BlockWeight(82.0, 80.4, -0.4, -0.6, -0.2, -0.5))

        assertEquals(Res.string.block_review_weight, line.res)
        assertEquals(listOf("82", "80,4", "−0,4", "−0,5"), line.args)
    }

    @Test
    fun weight_withoutTarget_dropsTheTarget() {
        val line = BlockReviewText.weight(BlockWeight(82.0, 80.4, -0.4, null, null, null))

        assertEquals(Res.string.block_review_weight_no_target, line.res)
        assertEquals(listOf("82", "80,4", "−0,4"), line.args)
    }

    @Test
    fun weight_withoutSlope_showsOnlyTheRange() {
        val line = BlockReviewText.weight(BlockWeight(82.0, 80.4, null, null, null, null))

        assertEquals(Res.string.block_review_weight_range, line.res)
    }

    @Test
    fun weight_withoutAverages_saysThereIsNoData() {
        assertEquals(Res.string.block_review_weight_none, BlockReviewText.weight(null).res)
        assertEquals(
            Res.string.block_review_weight_none,
            BlockReviewText.weight(BlockWeight(null, null, null, null, null, null)).res,
        )
    }

    @Test
    fun outcome_reachedAndNotReached_haveTextAndTone() {
        val reached = BlockReviewText.outcomeLabel(outcome(OutcomeKind.Weight, 78.0, 77.8, reached = true))
        val missed = BlockReviewText.outcomeLabel(outcome(OutcomeKind.Weight, 78.0, 80.0, reached = false))

        assertEquals(Res.string.block_review_outcome_reached, reached.res)
        assertEquals(BlockReviewText.OutcomeTone.Success, reached.tone)
        assertEquals(Res.string.block_review_outcome_not_reached, missed.res)
        assertEquals(BlockReviewText.OutcomeTone.Neutral, missed.tone)
    }

    @Test
    fun outcomeLine_forWeight_showsTargetAndCurrent() {
        val withNow = BlockReviewText.outcomeLine(outcome(OutcomeKind.Weight, 78.0, 80.3, false), null)
        val withoutNow = BlockReviewText.outcomeLine(outcome(OutcomeKind.Weight, 78.0, null, false), null)

        assertEquals(Res.string.block_review_outcome_weight_now, withNow?.res)
        assertEquals(listOf("78", "80,3"), withNow?.args)
        assertEquals(Res.string.block_review_outcome_weight, withoutNow?.res)
        assertEquals(listOf("78"), withoutNow?.args)
    }

    @Test
    fun outcomeLine_forALift_startsWithItsName() {
        val line = BlockReviewText.outcomeLine(outcome(OutcomeKind.E1rm, 120.0, 110.0, false), "Press banca")
        val bare = BlockReviewText.outcomeLine(outcome(OutcomeKind.E1rm, 120.0, null, false), "Press banca")

        assertEquals(Res.string.block_review_outcome_lift_now, line?.res)
        assertEquals(listOf("Press banca", "120", "110"), line?.args)
        assertEquals(Res.string.block_review_outcome_lift, bare?.res)
    }

    @Test
    fun outcomeLine_withoutNameOrTarget_isOmitted() {
        assertEquals(null, BlockReviewText.outcomeLine(outcome(OutcomeKind.E1rm, 120.0, 110.0, false), null))
        assertEquals(null, BlockReviewText.outcomeLine(outcome(OutcomeKind.Weight, null, 80.0, false), null))
    }

    @Test
    fun waist_showsSignedChangeAndStartDate() {
        val line = BlockReviewText.waist(BlockWaist(-2.0, LocalDate(2026, 10, 24), LocalDate(2026, 11, 21), false))

        assertEquals(Res.string.block_review_waist, line.res)
        assertEquals(listOf("−2,0", "24 oct"), line.args)
    }

    @Test
    fun waist_withinMargin_appendsTheMarginNote() {
        val line = BlockReviewText.waist(BlockWaist(1.5, LocalDate(2026, 10, 24), LocalDate(2026, 11, 21), true))

        assertEquals(Res.string.block_review_waist_margin, line.res)
        assertEquals(listOf("+1,5", "24 oct"), line.args)
    }

    @Test
    fun cutLength_countsTheDeficitWeeks() {
        val line = BlockReviewText.proposalReason(cutLengthProposal())

        assertEquals(Res.string.block_review_reason_cut_length, line.res)
        assertEquals(listOf("12"), line.args)
    }

    @Test
    fun floorLimited_showsTheRealAndTheTargetRate() {
        val proposal = PhaseProposal(
            Phase.Maintenance,
            PhaseChangeReason.FloorLimited,
            mapOf("slope_kg_week" to -0.2, "target_rate_kg_week" to -0.5),
        )

        val line = BlockReviewText.proposalReason(proposal)

        assertEquals(Res.string.block_review_reason_floor_limited, line.res)
        assertEquals(listOf("0,2", "0,5"), line.args)
    }

    @Test
    fun floorLimited_withStableOrRisingWeight_usesTheShortSentence() {
        val proposal = PhaseProposal(
            Phase.Maintenance,
            PhaseChangeReason.FloorLimited,
            mapOf("slope_kg_week" to 0.1, "target_rate_kg_week" to -0.5),
        )

        assertEquals(Res.string.block_review_reason_floor_limited_short, BlockReviewText.proposalReason(proposal).res)
    }

    @Test
    fun floorLimited_withMissingFigures_usesTheShortSentence() {
        val proposal = PhaseProposal(Phase.Maintenance, PhaseChangeReason.FloorLimited, emptyMap())

        assertEquals(Res.string.block_review_reason_floor_limited_short, BlockReviewText.proposalReason(proposal).res)
    }

    @Test
    fun backToGoal_hasOneSentenceWithoutArgs() {
        val proposal = PhaseProposal(Phase.Cut, PhaseChangeReason.BackToGoal, mapOf("blocks_in_maintenance" to 1.0))

        val line = BlockReviewText.proposalReason(proposal)

        assertEquals(Res.string.block_review_reason_back_to_goal, line.res)
        assertEquals(emptyList(), line.args)
    }
}
