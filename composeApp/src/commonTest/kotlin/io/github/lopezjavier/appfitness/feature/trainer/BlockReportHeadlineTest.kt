package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val TODAY = LocalDate(2026, 11, 22)

class BlockReportHeadlineTest {
    private val calorie = CalorieProposal("c1", "plan-1", 2800, 2600, -7.1, "rate_outside_interval", -0.1, null, null)

    private fun coach(
        review: BlockReview = blockReview(phaseProposal = null, progress = blockProgress()),
        live: Boolean = true,
        calorieProposal: CalorieProposal? = null,
    ) = blockReportOf(review, blockNames, calorieProposal, live, TODAY).coach

    private fun stall(id: String) = StallDiagnosis(id, StallCause.Sleep(6.0))

    @Test
    fun livePhaseProposal_winsOverEverything() {
        val review = blockReview(
            progress = blockProgress(missing = listOf(MissingKind.WeighIns)),
            stalls = listOf(stall("id-bench")),
        )

        val action = coach(review, calorieProposal = calorie).action

        assertEquals(BlockReportAction.PendingPhase(Phase.Maintenance), action)
    }

    @Test
    fun liveCalorieProposal_winsOverStallAndMissingData() {
        val review = blockReview(
            phaseProposal = null,
            progress = blockProgress(missing = listOf(MissingKind.WeighIns)),
            stalls = listOf(stall("id-bench")),
        )

        assertEquals(BlockReportAction.PendingCalorie(calorie), coach(review, calorieProposal = calorie).action)
    }

    @Test
    fun namedStall_winsOverMissingData() {
        val review = blockReview(
            phaseProposal = null,
            progress = blockProgress(missing = listOf(MissingKind.WeighIns)),
            stalls = listOf(stall("id-bench")),
        )

        assertEquals(BlockReportAction.Stall, coach(review).action)
    }

    @Test
    fun stallWithoutAName_isNotTheAction() {
        val review = blockReview(phaseProposal = null, progress = blockProgress(), stalls = listOf(stall("id-ghost")))

        assertEquals(BlockReportAction.CreateNext, coach(review).action)
    }

    @Test
    fun missingData_putsWeighInsBeforeFood() {
        val progress = blockProgress(missing = listOf(MissingKind.Food, MissingKind.WeighIns))
        val review = blockReview(phaseProposal = null, progress = progress)

        assertEquals(BlockReportAction.MissingData(MissingKind.WeighIns, past = false), coach(review).action)
        assertEquals(
            BlockReportAction.MissingData(MissingKind.WeighIns, past = true),
            coach(review, live = false).action,
        )
    }

    @Test
    fun nothingPending_createsTheNextBlock() {
        assertEquals(BlockReportAction.CreateNext, coach().action)
    }

    @Test
    fun livePhaseAlreadyDecided_fallsThroughToTheRest() {
        val review = blockReview(phaseDecision = PhaseDecision.Accepted, progress = blockProgress())

        assertEquals(BlockReportAction.CreateNext, coach(review).action)
    }

    @Test
    fun storedDecidedPhase_isTheFirstActionAsText() {
        val review = blockReview(
            phaseDecision = PhaseDecision.Kept,
            progress = blockProgress(missing = listOf(MissingKind.WeighIns)),
            stalls = listOf(stall("id-bench")),
        )

        assertEquals(
            BlockReportAction.DecidedPhase(PhaseDecision.Kept, Phase.Maintenance),
            coach(review, live = false).action,
        )
    }

    @Test
    fun storedUndecidedProposal_offersNoPendingAction() {
        val review = blockReview(progress = blockProgress())

        assertEquals(BlockReportAction.CreateNext, coach(review, live = false, calorieProposal = calorie).action)
    }

    @Test
    fun v1Review_getsTheSameActionLogicWithoutProgress() {
        val v1 = blockReview(phaseProposal = null, stalls = listOf(stall("id-bench")))

        assertEquals(BlockReportAction.Stall, coach(v1).action)
        assertEquals(BlockReportAction.CreateNext, coach(blockReview(phaseProposal = null)).action)
        assertNull(coach(v1).sentence)
    }

    @Test
    fun sentence_countsUpsOfNamedExercisesAndEqualsTheStrengthHeader() {
        val review = blockReview(progress = blockProgress())
        val ui = blockReportOf(review, blockNames, null, true, TODAY)

        val headerUps = assertNotNull(ui.progress).strength.groups.sumOf { it.up }

        assertEquals(headerUps, assertNotNull(ui.coach.sentence).ups)
        assertEquals(2, headerUps) // The unnamed "id-ghost" is not counted anywhere.
    }

    @Test
    fun sentence_mainLiftIsTheBestPercentageOfTheNamedLifts() {
        val sentence = assertNotNull(coach().sentence)

        val main = assertNotNull(sentence.mainLift)
        assertEquals("id-row", main.exerciseId) // +10 %; the unnamed ghost (+50 %) is ignored.
        assertEquals(8.0, main.changeKg)
        assertEquals(10.0, main.changePct)
    }

    @Test
    fun sentence_carriesTheBlockWeightChange() {
        assertEquals(-1.8, assertNotNull(coach().sentence).weightChangeKg)
    }

    @Test
    fun sentence_underTheWeightGate_hasNoWeightChange() {
        val review = blockReview(phaseProposal = null, progress = blockProgress(weight = blockWeight(daysLast = 2)))

        assertNull(assertNotNull(coach(review).sentence).weightChangeKg)
    }

    @Test
    fun sentence_inPlanWeekOne_hasNoUps() {
        val review = blockReview(phaseProposal = null, progress = blockProgress(blockWeek = 1))

        val sentence = assertNotNull(coach(review).sentence)

        assertNull(sentence.ups)
    }
}
