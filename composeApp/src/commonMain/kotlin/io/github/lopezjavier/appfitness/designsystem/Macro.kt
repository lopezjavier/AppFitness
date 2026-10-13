package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.graphics.Color

/** The three tracked macronutrients. Components take this instead of a free colour (05 §2.3). */
enum class Macro { Protein, Carbs, Fat }

object MacroDefaults {
    /** Protein lime, carbs cyan, fat amber: fixed by the design system. */
    fun color(macro: Macro, appColors: AppColors): Color = when (macro) {
        Macro.Protein -> appColors.macroProtein
        Macro.Carbs -> appColors.macroCarbs
        Macro.Fat -> appColors.macroFat
    }
}
