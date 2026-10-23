package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_rounding_body
import io.github.lopezjavier.appfitness.composeapp.resources.help_rounding_cd
import io.github.lopezjavier.appfitness.composeapp.resources.help_rounding_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_10
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_15
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_20
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_7_5
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_handle_state
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_rounding
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_rounding_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_rounding_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_dumbbell_rounding_state
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plate_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plate_rounding
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plate_rounding_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plate_rounding_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plate_rounding_state
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plates
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_plates_error
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_rounding_value
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_title
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppToggleChip
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import io.github.lopezjavier.appfitness.domain.usecase.GhostData
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val ROUNDING_STEP_KG = 0.5

/**
 * "Calculadoras" block of the training card: plate and dumbbell rounding, dumbbell handle, default bar and available
 * plates. All of it is a draft until "Guardar"; switching off the last plate is refused and shows [platesError].
 */
@Composable
internal fun CalculatorsBlock(
    settings: CalculatorSettings,
    platesError: Boolean,
    onEvent: (PreferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.preferences_calc_title),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HelpButton(
                contentDescription = stringResource(Res.string.help_rounding_cd),
                title = stringResource(Res.string.help_rounding_title),
                body = stringResource(Res.string.help_rounding_body),
                testTag = "rounding_help",
            )
        }
        RoundingRow(
            label = stringResource(Res.string.preferences_calc_plate_rounding),
            kg = settings.plateRoundingKg,
            state = Res.string.preferences_calc_plate_rounding_state,
            decreaseCd = stringResource(Res.string.preferences_calc_plate_rounding_decrease_cd),
            increaseCd = stringResource(Res.string.preferences_calc_plate_rounding_increase_cd),
            tag = "preferences_plate_rounding",
            onChange = { onEvent(PreferencesEvent.SetPlateRounding(it)) },
        )
        RoundingRow(
            label = stringResource(Res.string.preferences_calc_dumbbell_rounding),
            kg = settings.dumbbellRoundingKg,
            state = Res.string.preferences_calc_dumbbell_rounding_state,
            decreaseCd = stringResource(Res.string.preferences_calc_dumbbell_rounding_decrease_cd),
            increaseCd = stringResource(Res.string.preferences_calc_dumbbell_rounding_increase_cd),
            tag = "preferences_dumbbell_rounding",
            onChange = { onEvent(PreferencesEvent.SetDumbbellRounding(it)) },
        )
        RoundingRow(
            label = stringResource(Res.string.preferences_calc_dumbbell_handle),
            kg = settings.dumbbellHandleKg,
            state = Res.string.preferences_calc_dumbbell_handle_state,
            decreaseCd = stringResource(Res.string.preferences_calc_dumbbell_handle_decrease_cd),
            increaseCd = stringResource(Res.string.preferences_calc_dumbbell_handle_increase_cd),
            tag = "preferences_dumbbell_handle",
            onChange = { onEvent(PreferencesEvent.SetDumbbellHandle(it)) },
        )
        BarSelector(settings.barWeightKg) { onEvent(PreferencesEvent.SetBarWeight(it)) }
        PlateChips(settings.plateKg, platesError) { kg, on -> onEvent(PreferencesEvent.SetPlateEnabled(kg, on)) }
    }
}

@Composable
@Suppress("LongParameterList") // Label, value, three texts, tag and callback of one row.
private fun RoundingRow(
    label: String,
    kg: Double,
    state: StringResource,
    decreaseCd: String,
    increaseCd: String,
    tag: String,
    onChange: (Double) -> Unit,
) {
    val text = GhostData.weightText(kg)
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            valueText = stringResource(Res.string.preferences_calc_rounding_value, text),
            onDecrement = { onChange(kg - ROUNDING_STEP_KG) },
            onIncrement = { onChange(kg + ROUNDING_STEP_KG) },
            decrementEnabled = kg > MIN_ROUNDING_KG,
            incrementEnabled = kg < MAX_ROUNDING_KG,
            decrementContentDescription = decreaseCd,
            incrementContentDescription = increaseCd,
            stateDescription = stringResource(state, text),
            modifier = Modifier.testTag(tag),
            testTagPrefix = tag,
        )
    }
}

@Composable
private fun BarSelector(barKg: Double, onSelect: (Double) -> Unit) {
    val bars = CalculatorSettings.BARS_KG
    val options = listOf(
        AppOption(stringResource(Res.string.preferences_calc_bar_20), "preferences_bar_default_20"),
        AppOption(stringResource(Res.string.preferences_calc_bar_15), "preferences_bar_default_15"),
        AppOption(stringResource(Res.string.preferences_calc_bar_10), "preferences_bar_default_10"),
        AppOption(stringResource(Res.string.preferences_calc_bar_7_5), "preferences_bar_default_7_5"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.preferences_calc_bar),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        SegmentedSelector(
            options = options,
            selectedIndex = bars.indexOf(barKg).takeIf { it >= 0 },
            onSelect = { onSelect(bars[it]) },
            modifier = Modifier.testTag("preferences_bar_default"),
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class) // FlowRow wraps the ten chips.
private fun PlateChips(enabled: List<Double>, showError: Boolean, onToggle: (Double, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.preferences_calc_plates),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CalculatorSettings.PLATE_CATALOGUE.forEach { kg ->
                val text = GhostData.weightText(kg)
                AppToggleChip(
                    label = text,
                    checked = kg in enabled,
                    onCheckedChange = { onToggle(kg, it) },
                    contentDescription = stringResource(Res.string.preferences_calc_plate_cd, text),
                    modifier = Modifier.testTag("preferences_plate_${text.replace(',', '_')}"),
                )
            }
        }
        if (showError) {
            Text(
                text = stringResource(Res.string.preferences_calc_plates_error),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("preferences_plates_error"),
            )
        }
    }
}
