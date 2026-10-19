package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceMacrosTest {
    private val default = MacroSplit(30, 45, 25)

    @Test
    fun raisingProtein_sharesTheDropProportionallyWithLargestRemainder() {
        assertEquals(MacroSplit(35, 42, 23), BalanceMacros(default, Macronutrient.Protein, 35))
    }

    @Test
    fun loweringProtein_sumsTo100() {
        val result = BalanceMacros(default, Macronutrient.Protein, 25)
        assertEquals(25, result.proteinPct)
        assertEquals(100, result.proteinPct + result.carbsPct + result.fatPct)
    }

    @Test
    fun movingCarbsOrFat_balancesTheOtherTwo() {
        assertEquals(MacroSplit(27, 50, 23), BalanceMacros(default, Macronutrient.Carbs, 50))
        assertEquals(MacroSplit(26, 39, 35), BalanceMacros(default, Macronutrient.Fat, 35))
    }

    @Test
    fun requestAboveTheFeasibleMax_isClampedToNinety() {
        assertEquals(MacroSplit(90, 5, 5), BalanceMacros(default, Macronutrient.Protein, 95))
    }

    @Test
    fun bothOthersAtFive_leavesTheSplitUnchanged() {
        val split = MacroSplit(90, 5, 5)
        assertEquals(split, BalanceMacros(split, Macronutrient.Protein, 91))
        assertEquals(split, BalanceMacros(split, Macronutrient.Protein, 95))
    }

    @Test
    fun oneOtherAtFive_theRestGoesToTheOther() {
        assertEquals(MacroSplit(40, 55, 5), BalanceMacros(MacroSplit(30, 65, 5), Macronutrient.Protein, 40))
    }

    @Test
    fun otherThatWouldDropBelowFive_stopsAtFive() {
        assertEquals(MacroSplit(80, 5, 15), BalanceMacros(MacroSplit(30, 7, 63), Macronutrient.Protein, 80))
    }

    @Test
    fun requestBelowTheMinimum_isClampedToFive() {
        val result = BalanceMacros(default, Macronutrient.Fat, 2)
        assertEquals(5, result.fatPct)
        assertEquals(100, result.proteinPct + result.carbsPct + result.fatPct)
    }

    @Test
    fun noChange_returnsTheSameSplit() {
        assertEquals(default, BalanceMacros(default, Macronutrient.Carbs, 45))
    }

    @Test
    fun property_anyValidSplitAndRequest_staysValid() {
        val random = Random(SEED)
        var cases = 0
        while (cases < CASES) {
            val protein = random.nextInt(MIN, MAX + 1)
            val carbs = random.nextInt(MIN, MAX + 1)
            val fat = 100 - protein - carbs
            if (fat !in MIN..MAX) continue
            cases++
            val current = MacroSplit(protein, carbs, fat)
            val moved = Macronutrient.entries[random.nextInt(Macronutrient.entries.size)]
            val requested = random.nextInt(-10, 111)

            val result = BalanceMacros(current, moved, requested)

            val values = listOf(result.proteinPct, result.carbsPct, result.fatPct)
            assertEquals(100, values.sum(), "sum for $current $moved $requested")
            assertTrue(values.all { it in MIN..MAX }, "range for $current $moved $requested -> $result")
            assertEquals(requested.coerceIn(MIN, MAX), result[moved], "moved for $current $moved $requested")
        }
    }

    private companion object {
        const val SEED = 20_261_014
        const val CASES = 2_000
        const val MIN = 5
        const val MAX = 90
    }
}
