@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.live_calc_bar_title
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_10
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_15
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_20
import io.github.lopezjavier.appfitness.composeapp.resources.preferences_calc_bar_7_5
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.CalculatorSettings
import org.jetbrains.compose.resources.stringResource

private const val WEIGHT_MAX_DECIMALS = 1
private const val WEIGHT_MAX_INTEGER_DIGITS = 3

/** The "?" of a calculator sheet: description, body and tag. */
internal class CalculatorHelp(val contentDescription: String, val body: String, val testTag: String)

/**
 * Shared frame of both calculator sheets: heading, a scrolling body (weight field, bar selector, [results]) and the
 * [action] pinned below it. Closing the sheet (X, back, swipe, scrim) calls [onDismiss] and inserts nothing.
 */
@Composable
@Suppress("LongParameterList") // Frame slots of two sheets.
internal fun CalculatorSheetFrame(
    tag: String,
    title: String,
    weightLabel: String,
    calculator: CalculatorUiState,
    onEvent: (LiveSessionEvent) -> Unit,
    help: CalculatorHelp,
    results: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    AppBottomSheet(
        onDismissRequest = { onEvent(LiveSessionEvent.DismissCalculator) },
        testTagPrefix = "${tag}_sheet",
    ) {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    HelpButton(
                        contentDescription = help.contentDescription,
                        title = title,
                        body = help.body,
                        testTag = help.testTag,
                    )
                }
                NumberInput(
                    value = calculator.draft.weightText,
                    onValueChange = { onEvent(LiveSessionEvent.CalculatorWeightChanged(it)) },
                    label = weightLabel,
                    modifier = Modifier.fillMaxWidth().testTag("${tag}_weight_field"),
                    maxDecimals = WEIGHT_MAX_DECIMALS,
                    maxIntegerDigits = WEIGHT_MAX_INTEGER_DIGITS,
                )
                BarSelector(tag, calculator.draft.barKg) { onEvent(LiveSessionEvent.CalculatorBarChanged(it)) }
                results()
            }
            action()
        }
    }
}

@Composable
private fun BarSelector(tag: String, barKg: Double, onSelect: (Double) -> Unit) {
    val bars = CalculatorSettings.BARS_KG
    val options = listOf(
        AppOption(stringResource(Res.string.preferences_calc_bar_20), "${tag}_bar_20"),
        AppOption(stringResource(Res.string.preferences_calc_bar_15), "${tag}_bar_15"),
        AppOption(stringResource(Res.string.preferences_calc_bar_10), "${tag}_bar_10"),
        AppOption(stringResource(Res.string.preferences_calc_bar_7_5), "${tag}_bar_7_5"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(Res.string.live_calc_bar_title),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        SegmentedSelector(
            options = options,
            selectedIndex = bars.indexOf(barKg).takeIf { it >= 0 },
            onSelect = { onSelect(bars[it]) },
            modifier = Modifier.testTag("${tag}_bar"),
        )
    }
}
