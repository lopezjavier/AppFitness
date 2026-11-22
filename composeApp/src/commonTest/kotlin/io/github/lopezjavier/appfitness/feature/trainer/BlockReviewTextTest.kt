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
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlockReviewTextTest {
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

    private fun outcomeUi(kind: OutcomeKind, now: Double?) = OutcomeUi(kind, "k", 78.0, now, null, OutcomeChip.None)

    @Test
    fun outcomeLine_forWeight_showsTargetAndNow() {
        assertEquals(
            BlockReviewText.Line(Res.string.block_review_outcome_weight_now, listOf("78", "79,8")),
            BlockReviewText.outcomeLine(outcomeUi(OutcomeKind.Weight, 79.8), null),
        )
        assertEquals(
            BlockReviewText.Line(Res.string.block_review_outcome_weight, listOf("78")),
            BlockReviewText.outcomeLine(outcomeUi(OutcomeKind.Weight, null), null),
        )
    }

    @Test
    fun outcomeLine_forALift_startsWithItsNameAndIsOmittedWithoutOne() {
        assertEquals(
            BlockReviewText.Line(Res.string.block_review_outcome_lift_now, listOf("Press banca", "78", "70")),
            BlockReviewText.outcomeLine(outcomeUi(OutcomeKind.E1rm, 70.0), "Press banca"),
        )
        assertEquals(
            BlockReviewText.Line(Res.string.block_review_outcome_lift, listOf("Press banca", "78")),
            BlockReviewText.outcomeLine(outcomeUi(OutcomeKind.E1rm, null), "Press banca"),
        )
        assertNull(BlockReviewText.outcomeLine(outcomeUi(OutcomeKind.E1rm, 70.0), null))
    }
}
