package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.macro_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.macro_fat
import io.github.lopezjavier.appfitness.composeapp.resources.macro_protein
import io.github.lopezjavier.appfitness.composeapp.resources.profile_macros_title
import io.github.lopezjavier.appfitness.composeapp.resources.profile_macros_total
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroSlider
import io.github.lopezjavier.appfitness.designsystem.MacroSliderDefaults
import io.github.lopezjavier.appfitness.designsystem.MacroStackedBar
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.domain.model.Macronutrient
import org.jetbrains.compose.resources.stringResource

/**
 * "Distribución de Macros": the TOTAL pill, the stacked bar and one slider per macro. Moving a slider rebalances
 * the others (the ViewModel owns the maths); grams and kcal follow the live daily target.
 */
@Composable
internal fun MacroSplitCard(
    state: ProfileUiState.Loaded,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val split = state.form.split
    val targets = state.macroTargets
    val enabled = !state.isSaving
    AppCard(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.profile_macros_title),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextPill(stringResource(Res.string.profile_macros_total), Modifier.testTag("profile_macro_total"))
            MacroStackedBar(
                proteinPct = split.proteinPct,
                carbsPct = split.carbsPct,
                fatPct = split.fatPct,
                testTag = "profile_macro_bar",
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
                    onPercentChange = { onEvent(ProfileEvent.MoveMacro(macronutrient, it)) },
                    modifier = Modifier.testTag("profile_macro_${name}_slider"),
                    enabled = enabled,
                    valueTestTag = "profile_macro_${name}_value",
                )
            }
        }
    }
}
