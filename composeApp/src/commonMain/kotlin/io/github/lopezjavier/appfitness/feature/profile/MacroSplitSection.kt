package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.profile_macros_total
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroSlider
import io.github.lopezjavier.appfitness.designsystem.MacroSliderDefaults
import io.github.lopezjavier.appfitness.designsystem.MacroStackedBar
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import org.jetbrains.compose.resources.stringResource

/**
 * "Macros" editor content: the TOTAL pill, the stacked bar and one slider per macro. Moving a slider rebalances
 * the others (the ViewModel owns the maths); grams and kcal follow the live daily target.
 */
@Composable
internal fun MacroSplitSection(
    state: PreferencesUiState.Loaded,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val split = state.form.split
    val targets = state.macroTargets
    val enabled = !state.isSaving
    Column(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextPill(stringResource(Res.string.profile_macros_total), Modifier.testTag("preferences_macro_total"))
            MacroStackedBar(
                proteinPct = split.proteinPct,
                carbsPct = split.carbsPct,
                fatPct = split.fatPct,
                testTag = "preferences_macro_bar",
            )
        }
        Column(
            Modifier.padding(top = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MacroSliderDefaults.SliderGap),
        ) {
            val sliders = listOf(
                Triple(Macronutrient.Protein, Macro.Protein, Res.string.macro_protein),
                Triple(Macronutrient.Carbs, Macro.Carbs, Res.string.macro_carbs),
                Triple(Macronutrient.Fat, Macro.Fat, Res.string.macro_fat),
            )
            sliders.forEach { (macronutrient, macro, label) ->
                val grams = when (macronutrient) {
                    Macronutrient.Protein -> targets.proteinG to targets.proteinKcal
                    Macronutrient.Carbs -> targets.carbsG to targets.carbsKcal
                    Macronutrient.Fat -> targets.fatG to targets.fatKcal
                }
                val name = macronutrient.name.lowercase()
                MacroSlider(
                    label = stringResource(label),
                    macro = macro,
                    percent = split[macronutrient],
                    grams = grams.first,
                    kcal = grams.second,
                    onPercentChange = { onEvent(PreferencesEvent.MoveMacro(macronutrient, it)) },
                    modifier = Modifier.testTag("preferences_macro_${name}_slider"),
                    enabled = enabled,
                    valueTestTag = "preferences_macro_${name}_value",
                )
            }
        }
    }
}
