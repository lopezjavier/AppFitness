package io.github.lopezjavier.appfitness.feature.diary

import io.github.lopezjavier.appfitness.domain.model.MealTotals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiarySummaryTest {
    @Test
    fun of_roundsEveryValueHalfUp() {
        val summary = DiarySummary.of(MealTotals(1839.6, 79.5, 149.4, 40.05), targetKcal = 2100)

        assertEquals(DiarySummary(1840, 2100, 80, 149, 40), summary)
    }

    @Test
    fun of_withoutGoals_hasNoTarget() {
        assertNull(DiarySummary.of(MealTotals.ZERO, targetKcal = null).targetKcal)
    }

    @Test
    fun text_withGoals_showsConsumedOverTargetAndMacros() {
        assertEquals(
            "1.240 / 2.100 kcal · P 80 · C 150 · G 40",
            DiarySummary(1240, 2100, 80, 150, 40).text,
        )
    }

    @Test
    fun text_withoutGoals_showsConsumedOnly() {
        assertEquals("1.240 kcal · P 80 · C 150 · G 40", DiarySummary(1240, null, 80, 150, 40).text)
    }
}
