package io.github.lopezjavier.appfitness.feature.goals

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_label
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_state
import io.github.lopezjavier.appfitness.composeapp.resources.energy_adjust_value
import io.github.lopezjavier.appfitness.composeapp.resources.energy_floor_warning
import io.github.lopezjavier.appfitness.composeapp.resources.energy_heading
import io.github.lopezjavier.appfitness.composeapp.resources.energy_intensity_aggressive
import io.github.lopezjavier.appfitness.composeapp.resources.energy_intensity_light
import io.github.lopezjavier.appfitness.composeapp.resources.energy_intensity_moderate
import io.github.lopezjavier.appfitness.composeapp.resources.energy_pill_deficit
import io.github.lopezjavier.appfitness.composeapp.resources.energy_pill_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.energy_pill_surplus
import io.github.lopezjavier.appfitness.composeapp.resources.energy_strategy_deficit
import io.github.lopezjavier.appfitness.composeapp.resources.energy_strategy_maintenance
import io.github.lopezjavier.appfitness.composeapp.resources.energy_strategy_surplus
import io.github.lopezjavier.appfitness.composeapp.resources.energy_target
import io.github.lopezjavier.appfitness.composeapp.resources.energy_tdee
import io.github.lopezjavier.appfitness.composeapp.resources.ic_warning
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.domain.model.GoalStrategy
import io.github.lopezjavier.appfitness.domain.usecase.AdjustmentIntensity
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private val WarningIconSize = Spacing.lg + Spacing.xs

/**
 * "Estrategia energética": TDEE, strategy selector, adjustment stepper, daily target, adjustment pill and the
 * safety-floor warning. Stateless: Onboarding and Profile own the state. Tags (prefix = [testTagPrefix]):
 * `_tdee`, `_strategy_<deficit|maintenance|surplus>`, `_adjust_decrement|_increment|_value`, `_target`, `_pill`,
 * `_floor_warning`.
 */
@Composable
fun EnergyStrategySection(
    state: EnergyStrategyUiState,
    onEvent: (EnergyStrategyEvent) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.energy_heading),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.energy_tdee, AppNumberFormat.integer(state.tdeeKcal)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs).testTag("${testTagPrefix}_tdee"),
        )
        Column(
            Modifier.padding(top = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            StrategySelector(state.strategy, testTagPrefix) { onEvent(EnergyStrategyEvent.SelectStrategy(it)) }
            AdjustmentRow(state, onEvent, testTagPrefix)
            TargetText(state.targetKcal, Modifier.testTag("${testTagPrefix}_target"))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextPill(pillText(state), Modifier.testTag("${testTagPrefix}_pill"))
                if (state.isFloored) FloorWarning(state.floorKcal, Modifier.testTag("${testTagPrefix}_floor_warning"))
            }
        }
    }
}

@Composable
private fun StrategySelector(selected: GoalStrategy, prefix: String, onSelect: (GoalStrategy) -> Unit) {
    val options =
        listOf(
            AppOption(stringResource(Res.string.energy_strategy_deficit), "${prefix}_strategy_deficit"),
            AppOption(stringResource(Res.string.energy_strategy_maintenance), "${prefix}_strategy_maintenance"),
            AppOption(stringResource(Res.string.energy_strategy_surplus), "${prefix}_strategy_surplus"),
        )
    SegmentedSelector(options, selected.ordinal, onSelect = { onSelect(GoalStrategy.entries[it]) })
}

@Composable
private fun AdjustmentRow(state: EnergyStrategyUiState, onEvent: (EnergyStrategyEvent) -> Unit, prefix: String) {
    val signed = AppNumberFormat.signedInteger(state.adjustment)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.energy_adjust_label),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            valueText = stringResource(Res.string.energy_adjust_value, signed),
            onDecrement = { onEvent(EnergyStrategyEvent.StepDown) },
            onIncrement = { onEvent(EnergyStrategyEvent.StepUp) },
            decrementEnabled = state.canStepDown,
            incrementEnabled = state.canStepUp,
            decrementContentDescription = stringResource(Res.string.energy_adjust_decrease_cd),
            incrementContentDescription = stringResource(Res.string.energy_adjust_increase_cd),
            stateDescription = stringResource(Res.string.energy_adjust_state, signed),
            testTagPrefix = "${prefix}_adjust",
        )
    }
}

/** One text node: the label and unit in `bodyMedium`, the number in `headlineMetric` lime. */
@Composable
private fun TargetText(targetKcal: Int, modifier: Modifier = Modifier) {
    val number = AppNumberFormat.integer(targetKcal)
    val full = stringResource(Res.string.energy_target, number)
    val secondary = SpanStyle(color = AppTheme.colors.textSecondary)
    val metric = AppTheme.typography.headlineMetric.toSpanStyle().copy(color = AppTheme.colors.neonLime)
    val start = full.indexOf(number)
    val text =
        buildAnnotatedString {
            withStyle(secondary) { append(full.substring(0, start)) }
            withStyle(metric) { append(number) }
            withStyle(secondary) { append(full.substring(start + number.length)) }
        }
    Text(text = text, style = AppTheme.typography.bodyMedium, modifier = modifier)
}

@Composable
private fun pillText(state: EnergyStrategyUiState): String {
    val adjustment = AppNumberFormat.signedInteger(state.adjustment)
    val intensity =
        when (state.intensity) {
            AdjustmentIntensity.Light -> stringResource(Res.string.energy_intensity_light)
            AdjustmentIntensity.Moderate -> stringResource(Res.string.energy_intensity_moderate)
            AdjustmentIntensity.Aggressive -> stringResource(Res.string.energy_intensity_aggressive)
            null -> ""
        }
    return when (state.strategy) {
        GoalStrategy.Deficit -> stringResource(Res.string.energy_pill_deficit, adjustment, intensity)
        GoalStrategy.Maintenance -> stringResource(Res.string.energy_pill_maintenance, adjustment)
        GoalStrategy.Surplus -> stringResource(Res.string.energy_pill_surplus, adjustment, intensity)
    }
}

@Composable
private fun FloorWarning(floorKcal: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            imageVector = vectorResource(Res.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(WarningIconSize),
            colorFilter = ColorFilter.tint(AppTheme.colors.energyAmber),
        )
        Text(
            text = stringResource(Res.string.energy_floor_warning, AppNumberFormat.integer(floorKcal)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.energyAmber,
        )
    }
}

@Preview
@Composable
private fun EnergyStrategySectionPreview() {
    AppTheme {
        EnergyStrategySection(
            state = EnergyStrategyUiState(
                tdeeKcal = 1800,
                strategy = GoalStrategy.Deficit,
                adjustment = -400,
                targetKcal = 1500,
                isFloored = true,
                floorKcal = 1500,
                intensity = AdjustmentIntensity.Moderate,
                canStepDown = false,
                canStepUp = true,
            ),
            onEvent = {},
            testTagPrefix = "preview_goals",
            modifier = Modifier.padding(Spacing.screenMargin),
        )
    }
}
