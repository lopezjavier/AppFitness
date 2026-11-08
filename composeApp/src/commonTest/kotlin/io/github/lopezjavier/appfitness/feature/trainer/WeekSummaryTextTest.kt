package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_calories_above_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_calories_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_calories_below_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_food_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_food_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_keep_plan_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_plateau_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_proposal_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_weekend_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_weekend_note
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_weigh_in_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_diagnosis_weigh_in_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_maintain_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_maintain_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_none_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_none_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_protein_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_protein_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sessions_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sessions_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sessions_one_day_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sets_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sets_cause
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sleep_action
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_stall_sleep_cause
import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import io.github.lopezjavier.appfitness.domain.model.StallCause
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeekSummaryTextTest {
    private val weekStart = LocalDate(2027, 1, 4)

    @Test
    fun diagnosisLine_loggingFood_carriesTheCountAndTheWeekDate() {
        val line = WeekSummaryText.diagnosisLine(NutritionDiagnosis.LoggingFood(3, weekStart), hasProposal = true)

        assertEquals(Res.string.week_summary_diagnosis_food_cause, line.cause)
        assertEquals(listOf("3", "4 ene"), line.causeArgs)
        assertEquals(Res.string.week_summary_diagnosis_food_action, line.action)
        assertNull(line.note)
    }

    @Test
    fun diagnosisLine_loggingWeighIn_carriesTheCountAndTheWeekDate() {
        val line = WeekSummaryText.diagnosisLine(NutritionDiagnosis.LoggingWeighIn(2, weekStart), hasProposal = false)

        assertEquals(Res.string.week_summary_diagnosis_weigh_in_cause, line.cause)
        assertEquals(listOf("2", "4 ene"), line.causeArgs)
        assertEquals(Res.string.week_summary_diagnosis_weigh_in_action, line.action)
    }

    @Test
    fun diagnosisLine_caloriesAbove_usesTheGroupedFiguresAndTheGoalAction() {
        val line = WeekSummaryText.diagnosisLine(
            NutritionDiagnosis.Calories(2350, 2000, 350, above = true, weekend = false),
            hasProposal = true,
        )

        assertEquals(Res.string.week_summary_diagnosis_calories_above_cause, line.cause)
        assertEquals(listOf("2.350", "350"), line.causeArgs)
        assertEquals(Res.string.week_summary_diagnosis_calories_action, line.action)
        assertEquals(listOf("2.000"), line.actionArgs)
        assertNull(line.note)
    }

    @Test
    fun diagnosisLine_caloriesBelow_usesTheLessThanWording() {
        val line = WeekSummaryText.diagnosisLine(
            NutritionDiagnosis.Calories(1700, 2000, 300, above = false, weekend = false),
            hasProposal = false,
        )

        assertEquals(Res.string.week_summary_diagnosis_calories_below_cause, line.cause)
        assertEquals(listOf("1.700", "300"), line.causeArgs)
    }

    @Test
    fun diagnosisLine_caloriesWeekend_addsTheNoteAndTheWeekendAction() {
        val line = WeekSummaryText.diagnosisLine(
            NutritionDiagnosis.Calories(2350, 2000, 350, above = true, weekend = true),
            hasProposal = false,
        )

        assertEquals(Res.string.week_summary_diagnosis_weekend_note, line.note)
        assertEquals(Res.string.week_summary_diagnosis_weekend_action, line.action)
        assertTrue(line.actionArgs.isEmpty())
    }

    @Test
    fun diagnosisLine_plateau_showsTheTrendAndRangeWithTwoDecimals() {
        val line = WeekSummaryText.diagnosisLine(NutritionDiagnosis.Plateau(-0.02, -0.12, 0.09), hasProposal = false)

        assertEquals(Res.string.week_summary_diagnosis_plateau_cause, line.cause)
        assertEquals(listOf("−0,02", "−0,12", "0,09"), line.causeArgs)
    }

    @Test
    fun diagnosisLine_plateauAndNoCause_pointToTheProposalOrKeepThePlan() {
        val plateau = NutritionDiagnosis.Plateau(0.0, -0.1, 0.1)

        assertEquals(
            Res.string.week_summary_diagnosis_proposal_action,
            WeekSummaryText.diagnosisLine(plateau, hasProposal = true).action,
        )
        assertEquals(
            Res.string.week_summary_diagnosis_keep_plan_action,
            WeekSummaryText.diagnosisLine(plateau, hasProposal = false).action,
        )
        assertEquals(
            Res.string.week_summary_diagnosis_proposal_action,
            WeekSummaryText.diagnosisLine(NutritionDiagnosis.NoCause, hasProposal = true).action,
        )
        assertEquals(
            Res.string.week_summary_diagnosis_keep_plan_action,
            WeekSummaryText.diagnosisLine(NutritionDiagnosis.NoCause, hasProposal = false).action,
        )
    }

    @Test
    fun diagnosisLine_noCause_reusesTheStallNoCauseSentence() {
        val line = WeekSummaryText.diagnosisLine(NutritionDiagnosis.NoCause, hasProposal = false)

        assertEquals(Res.string.week_summary_stall_none_cause, line.cause)
        assertTrue(line.causeArgs.isEmpty())
    }

    @Test
    fun sets_showWholeNumbersAndHalvesWithDecimalComma() {
        assertEquals("10", WeekSummaryText.sets(10.0))
        assertEquals("2,5", WeekSummaryText.sets(2.5))
    }

    @Test
    fun signedKg_usesTheRealMinusAndPlusForGains() {
        assertEquals("−0,3", WeekSummaryText.signedKg(-0.3))
        assertEquals("+0,2", WeekSummaryText.signedKg(0.2))
        assertEquals("0", WeekSummaryText.signedKg(0.0))
    }

    @Test
    fun kg_hasOneDecimal() {
        assertEquals("80,2", WeekSummaryText.kg(80.2))
    }

    @Test
    fun kcalDelta_carriesItsSign() {
        assertEquals("+100", WeekSummaryText.kcalDelta(weekDecision().proposal))
    }

    @Test
    fun kcalDifference_carriesItsSignWithTheRealMinus() {
        assertEquals("−90", WeekSummaryText.kcalDifference(2310, 2400))
        assertEquals("+120", WeekSummaryText.kcalDifference(2520, 2400))
        assertEquals("0", WeekSummaryText.kcalDifference(2400, 2400))
    }

    @Test
    fun proteinGrams_areWholeNumbers() {
        assertEquals("149", WeekSummaryText.proteinGrams(148.5))
        assertEquals("1.200", WeekSummaryText.proteinGrams(1200.0))
    }

    @Test
    fun perKg_usesDecimalComma() {
        assertEquals("1,8", WeekSummaryText.perKg(1.8))
        assertEquals("1,85", WeekSummaryText.perKg(1.85))
    }

    @Test
    fun visibleGroups_omitGroupsWithZeroSets() {
        val groups = listOf(GroupSets(MuscleGroup.Chest, 10.0), GroupSets(MuscleGroup.Calves, 0.0))

        assertEquals(listOf(MuscleGroup.Chest), WeekSummaryText.visibleGroups(groups).map { it.muscleGroup })
    }

    @Test
    fun separateDeloadLine_isUsedOnlyWhenNoDeloadRowIsInTheChanges() {
        val withRow = weekSummary(
            deload = true,
            changes = listOf(coachChange("d", CoachChangeKind.DeloadScheduled, exerciseId = "")),
        )

        assertTrue(WeekSummaryText.showsSeparateDeload(weekSummary(deload = true)))
        assertFalse(WeekSummaryText.showsSeparateDeload(withRow))
        assertFalse(WeekSummaryText.showsSeparateDeload(weekSummary(deload = false)))
    }

    @Test
    fun stallLine_sessions_usesOneDecimalWithCommaAndTheTwoDayActionForAOneDayPlan() {
        val usual = WeekSummaryText.stallLine(StallCause.Sessions(1.5, planDays = 3))
        val oneDay = WeekSummaryText.stallLine(StallCause.Sessions(1.0, planDays = 1))

        assertEquals(Res.string.week_summary_stall_sessions_cause, usual.cause)
        assertEquals(listOf("1,5"), usual.causeArgs)
        assertEquals(Res.string.week_summary_stall_sessions_action, usual.action)
        assertEquals(Res.string.week_summary_stall_sessions_one_day_action, oneDay.action)
    }

    @Test
    fun stallLine_sets_carriesTheAverageAndTheLowercaseGroup() {
        val line = WeekSummaryText.stallLine(StallCause.Sets(7.0, MuscleGroup.Chest))

        assertEquals(Res.string.week_summary_stall_sets_cause, line.cause)
        assertEquals(listOf("7"), line.causeArgs)
        assertEquals(MuscleGroup.Chest, line.group)
        assertTrue(line.groupLowercase)
        assertEquals(Res.string.week_summary_stall_sets_action, line.action)
    }

    @Test
    fun stallLine_maintain_keepsTheCapitalisedGroupAtTheStart() {
        val line = WeekSummaryText.stallLine(StallCause.GroupOnMaintain(MuscleGroup.Back))

        assertEquals(Res.string.week_summary_stall_maintain_cause, line.cause)
        assertEquals(MuscleGroup.Back, line.group)
        assertFalse(line.groupLowercase)
        assertEquals(Res.string.week_summary_stall_maintain_action, line.action)
    }

    @Test
    fun stallLine_protein_showsGramsPerKgWithCommaAndTheTarget() {
        val line = WeekSummaryText.stallLine(StallCause.Protein(avgG = 120, perKg = 1.4, targetG = 160))

        assertEquals(Res.string.week_summary_stall_protein_cause, line.cause)
        assertEquals(listOf("120", "1,4"), line.causeArgs)
        assertEquals(Res.string.week_summary_stall_protein_action, line.action)
        assertEquals(listOf("160"), line.actionArgs)
    }

    @Test
    fun stallLine_protein_justBelowTheThreshold_keepsTwoDecimals() {
        val line = WeekSummaryText.stallLine(StallCause.Protein(avgG = 127, perKg = 1.59, targetG = 160))

        assertEquals(listOf("127", "1,59"), line.causeArgs)
    }

    @Test
    fun stallLine_sleep_usesOneDecimalWithComma() {
        val line = WeekSummaryText.stallLine(StallCause.Sleep(6.2))

        assertEquals(Res.string.week_summary_stall_sleep_cause, line.cause)
        assertEquals(listOf("6,2"), line.causeArgs)
        assertEquals(Res.string.week_summary_stall_sleep_action, line.action)
    }

    @Test
    fun stallLine_noClearCause_addsTheMissingFoodLineOnlyWhenTheGateFailed() {
        val gated = WeekSummaryText.stallLine(StallCause.NoClearCause(proteinGateMissing = true))
        val plain = WeekSummaryText.stallLine(StallCause.NoClearCause(proteinGateMissing = false))

        assertEquals(Res.string.week_summary_stall_none_cause, gated.cause)
        assertEquals(Res.string.week_summary_stall_none_action, gated.action)
        assertTrue(gated.missingFood)
        assertFalse(plain.missingFood)
    }
}
