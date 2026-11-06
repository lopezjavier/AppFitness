package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CoachChangeKind
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekSummaryTextTest {
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
        assertEquals("+100", WeekSummaryText.kcalDelta(weekProposal().proposal))
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
}
