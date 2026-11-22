package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.MissingKind
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import io.github.lopezjavier.appfitness.domain.model.ProgressState
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.StallCause
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.StrengthGroup
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekWeightTrend
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekReportHeadlineTest {
    private val pending = weekDecision(ProposalStatus.Pending)
    private val diagnosis = NutritionDiagnosis.LoggingFood(3, LocalDate(2026, 11, 3))
    private val stall = StallDiagnosis("id-bench", StallCause.Sessions(0.0, 2))

    // Everything that can be an action at once; each test removes the winners above it.
    private fun everything(): WeekSummary =
        v2Summary(weekProgress(missing = listOf(MissingKind.WeighIns)), deload = true)
            .copy(nutrition = weekNutrition(decisions = listOf(pending), diagnosis = diagnosis), stalls = listOf(stall))

    private fun action(summary: WeekSummary, live: Boolean = true) =
        weekReportHeadline(summary, summary.progress, live).action

    private fun sentence(progress: io.github.lopezjavier.appfitness.domain.model.WeekProgress) =
        weekReportHeadline(v2Summary(progress), progress, live = true).sentence!!

    @Test
    fun pendingProposal_winsOverEverything() {
        assertIs<WeekReportAction.PendingProposal>(action(everything()))
    }

    @Test
    fun diagnosis_winsOverStallMissingAndDeload() {
        val summary = everything().let { it.copy(nutrition = it.nutrition!!.copy(decisions = emptyList())) }

        assertEquals(WeekReportAction.WeightDiagnosis, action(summary))
    }

    @Test
    fun stall_winsOverMissingAndDeload() {
        val summary = everything().let {
            it.copy(nutrition = it.nutrition!!.copy(decisions = emptyList(), diagnosis = null))
        }

        assertEquals(WeekReportAction.Stall, action(summary))
    }

    @Test
    fun missingData_winsOverDeload() {
        val summary = everything().copy(nutrition = weekNutrition(), stalls = emptyList())

        assertEquals(WeekReportAction.MissingData(MissingKind.WeighIns), action(summary))
    }

    @Test
    fun deload_winsOverOnPlan() {
        assertEquals(WeekReportAction.Deload, action(v2Summary(deload = true)))
    }

    @Test
    fun nothingToDo_isOnPlan() {
        assertEquals(WeekReportAction.OnPlan, action(v2Summary()))
    }

    @Test
    fun fixableMissingDataComesBeforeTheDeletedLines() {
        assertEquals(
            WeekReportAction.MissingData(MissingKind.Food),
            action(v2Summary(weekProgress(missing = listOf(MissingKind.DeletedSessions, MissingKind.Food)))),
        )
        assertEquals(
            WeekReportAction.MissingData(MissingKind.DeletedWeighIns),
            action(v2Summary(weekProgress(missing = listOf(MissingKind.DeletedWeighIns)))),
        )
    }

    @Test
    fun pastReport_readsADecidedProposalAsItsOutcome_neverAButton() {
        val summary = v2Summary().copy(
            nutrition = weekNutrition(decisions = listOf(weekDecision(ProposalStatus.Applied))),
        )

        val result = assertIs<WeekReportAction.DecidedProposal>(action(summary, live = false))

        assertEquals(ProposalStatus.Applied, result.status)
    }

    @Test
    fun pastReport_aPendingProposalKeepsItsUnknownOutcomeAsText() {
        val summary = v2Summary().copy(nutrition = weekNutrition(decisions = listOf(pending)))

        val result = assertIs<WeekReportAction.DecidedProposal>(action(summary, live = false))

        assertEquals(ProposalStatus.Pending, result.status)
    }

    @Test
    fun pastReport_fixableMissingDataIsInformationNotACallToAction() {
        val summary = v2Summary(weekProgress(missing = listOf(MissingKind.WeighIns)))

        assertEquals(WeekReportAction.MissingData(MissingKind.WeighIns, past = true), action(summary, live = false))
        assertEquals(WeekReportAction.MissingData(MissingKind.WeighIns, past = false), action(summary, live = true))
    }

    @Test
    fun v1Report_hasNoSentenceAndTakesTheActionFromTheKeptFields() {
        val summary = weekSummary(nutrition = weekNutrition(completeDays = 2)).copy(weighInDays = 1)

        val card = weekReportHeadline(summary, null, live = true)

        assertNull(card.sentence)
        assertEquals(WeekReportAction.MissingData(MissingKind.WeighIns), card.action)
    }

    @Test
    fun sentence_countsUpOverComparedExercises() {
        assertEquals(1, sentence(weekProgress()).strengthUp)
    }

    @Test
    fun sentence_countsCarriedRowsLikeAnyOther() {
        val carried = exerciseProgress(
            "id-row",
            ProgressState.Up,
            best = progressSession(60.0, 10, date = LocalDate(2026, 11, 2)),
        )
        val groups = listOf(
            StrengthGroup(MuscleGroup.Back, listOf(carried, exerciseProgress("id-bench", ProgressState.Up))),
        )

        assertEquals(2, sentence(weekProgress(groups)).strengthUp)
    }

    @Test
    fun sentence_noComparedExercise_leavesTheCountOut() {
        val groups =
            listOf(StrengthGroup(MuscleGroup.Chest, listOf(exerciseProgress("id-a", ProgressState.Uncompared))))

        assertNull(sentence(weekProgress(groups)).strengthUp)
    }

    @Test
    fun sentence_weightChangeOnlyWhenTheGateHolds() {
        assertEquals(-0.3, sentence(weekProgress()).weightChangeKg)
        assertNull(sentence(weekProgress(weight = weekWeight(changeKg = null))).weightChangeKg)
    }

    @Test
    fun sentence_onPaceComesFromTheSharedVerdict() {
        // 80, 79, 79, 78 (slope -0.6, band [-1.209, 0.009]) holds both the cut rate and zero: no verdict yet.
        assertNull(sentence(weekProgress()).onPace)

        val onPace = weekProgress(weight = weekWeight(trend = WeekWeightTrend(-0.5, -0.7, -0.3, 5)))
        val slower = weekProgress(weight = weekWeight(trend = WeekWeightTrend(-0.1, -0.2, -0.05, 5)))

        assertEquals(true, sentence(onPace).onPace)
        assertEquals(false, sentence(slower).onPace)
        assertNull(sentence(weekProgress(weight = weekWeight(trend = null))).onPace)
    }

    @Test
    fun weekOne_isTheBaseSentenceWithNoFigures() {
        val sentence = sentence(weekProgress(blockWeek = 1))

        assertTrue(sentence.base)
        assertNull(sentence.strengthUp)
        assertNull(sentence.weightChangeKg)
        assertNull(sentence.onPace)
    }
}
