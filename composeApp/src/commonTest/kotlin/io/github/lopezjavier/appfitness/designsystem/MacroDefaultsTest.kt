package io.github.lopezjavier.appfitness.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals

class MacroDefaultsTest {
    private val c = KineticDarkColors

    @Test
    fun color_protein_isLime() {
        assertEquals(c.macroProtein, MacroDefaults.color(Macro.Protein, c))
        assertEquals(c.neonLime, MacroDefaults.color(Macro.Protein, c))
    }

    @Test
    fun color_carbs_isCyan() {
        assertEquals(c.macroCarbs, MacroDefaults.color(Macro.Carbs, c))
        assertEquals(c.athleticCyan, MacroDefaults.color(Macro.Carbs, c))
    }

    @Test
    fun color_fat_isAmber() {
        assertEquals(c.macroFat, MacroDefaults.color(Macro.Fat, c))
        assertEquals(c.energyAmber, MacroDefaults.color(Macro.Fat, c))
    }
}
