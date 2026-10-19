package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.model.MacroSplit
import io.github.lopezjavier.appfitness.domain.model.MacroTargets
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateMacroTargetsTest {
    @Test
    fun defaultSplit_of2400() {
        assertEquals(
            MacroTargets(180, 270, 67, 720, 1080, 600),
            CalculateMacroTargets(2400, MacroSplit(30, 45, 25)),
        )
    }

    @Test
    fun raisedProtein_of2400() {
        val targets = CalculateMacroTargets(2400, MacroSplit(35, 42, 23))
        assertEquals(210, targets.proteinG)
        assertEquals(252, targets.carbsG)
        assertEquals(61, targets.fatG)
    }

    @Test
    fun halfRoundsUp() {
        // 2410 * 5 % = 120.5 kcal -> 121 kcal; / 4 = 30.125 g -> 30; fat 120.5 / 9 = 13.39 -> 13
        val targets = CalculateMacroTargets(2410, MacroSplit(5, 5, 90))
        assertEquals(121, targets.proteinKcal)
        assertEquals(30, targets.proteinG)
        // 10 kcal at 5 % = 0.5 kcal -> 1 kcal; 0.5 / 4 = 0.125 g -> 0
        assertEquals(1, CalculateMacroTargets(10, MacroSplit(5, 5, 90)).proteinKcal)
        // 1500 kcal at 30 % = 450 kcal; 450 / 4 = 112.5 g -> 113
        assertEquals(113, CalculateMacroTargets(1500, MacroSplit(30, 45, 25)).proteinG)
    }
}
