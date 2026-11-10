package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_abv
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_converted
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_help_title
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_mode_abv
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_mode_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_alcohol_mode_g
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val ALCOHOL_DECIMALS = 1

/**
 * The content of the Alcohol section (ADR-106): grams per 100 g or ml; an ml food may be typed as grados (% vol)
 * instead, converted by the core. A g food shows the grams field only.
 */
@Composable
internal fun AlcoholSectionContent(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    val grados = state.baseUnit == "ml" && state.alcoholMode == AlcoholMode.Grados
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (state.baseUnit == "ml") AlcoholModeSelector(grados, onEvent)
        if (grados) {
            NutrientField(
                NutrientSpec(
                    stringResource(Res.string.create_food_alcohol_abv),
                    null,
                    "create_food_alcohol_abv_field",
                    decimals = ALCOHOL_DECIMALS,
                ),
                state.alcoholAbv,
                state.errors.alcohol,
                onChange = { onEvent(CreateFoodEvent.AlcoholAbvChanged(it)) },
                imeAction = ImeAction.Done,
                help = { AlcoholHelp() },
            )
            state.alcoholConvertedG?.let {
                Text(
                    text = stringResource(Res.string.create_food_alcohol_converted, it),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("create_food_alcohol_converted"),
                )
            }
        } else {
            NutrientField(
                NutrientSpec(
                    stringResource(Res.string.create_food_alcohol),
                    null,
                    "create_food_alcohol_field",
                    decimals = ALCOHOL_DECIMALS,
                ),
                state.alcohol,
                state.errors.alcohol,
                onChange = { onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Alcohol, it)) },
                imeAction = ImeAction.Done,
                help = { AlcoholHelp() },
            )
        }
    }
}

@Composable
private fun AlcoholModeSelector(grados: Boolean, onEvent: (CreateFoodEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(Res.string.create_food_alcohol_mode_cd),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        SegmentedSelector(
            options = listOf(
                AppOption(stringResource(Res.string.create_food_alcohol_mode_g), "create_food_alcohol_mode_g"),
                AppOption(
                    stringResource(Res.string.create_food_alcohol_mode_abv),
                    "create_food_alcohol_mode_abv",
                ),
            ),
            selectedIndex = if (grados) 1 else 0,
            onSelect = {
                onEvent(
                    CreateFoodEvent.AlcoholModeSelected(if (it == 1) AlcoholMode.Grados else AlcoholMode.Grams),
                )
            },
            modifier = Modifier.testTag("create_food_alcohol_mode_selector"),
        )
    }
}

@Composable
private fun AlcoholHelp() {
    HelpButton(
        contentDescription = stringResource(Res.string.create_food_alcohol_help_cd),
        title = stringResource(Res.string.create_food_alcohol_help_title),
        body = stringResource(Res.string.create_food_alcohol_help_body),
        testTag = "create_food_alcohol_help",
    )
}
