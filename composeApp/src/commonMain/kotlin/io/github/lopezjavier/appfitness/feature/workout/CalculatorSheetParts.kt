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
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.WarmUpSet

private const val WEIGHT_MAX_DECIMALS = 1
private const val WEIGHT_MAX_INTEGER_DIGITS = 3

/** The "?" of a calculator sheet: description, body and tag. */
internal class CalculatorHelp(val contentDescription: String, val body: String, val testTag: String)

/**
 * What the calculator sheets can ask of their host (the live session or the routine builder); the sheets know
 * nothing about either screen's events.
 */
internal class CalculatorActions(
    val onWeight: (String) -> Unit,
    val onDiscCount: (weightKg: Double, count: Int) -> Unit,
    val onWarmUpSets: (Int) -> Unit,
    val onAddWarmUp: (List<WarmUpSet>) -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Shared frame of both calculator sheets: heading, a scrolling body (weight field, [results]) and the
 * [action] pinned below it. Closing the sheet (X, back, swipe, scrim) calls `actions.onDismiss` and inserts nothing.
 * There is no bar selector: the bar comes from Preferencias (ADR-56). The disc loader passes
 * [showWeightField] = false.
 */
@Composable
@Suppress("LongParameterList") // Frame slots of two sheets.
internal fun CalculatorSheetFrame(
    tag: String,
    title: String,
    weightLabel: String,
    calculator: CalculatorUiState,
    actions: CalculatorActions,
    help: CalculatorHelp,
    results: @Composable () -> Unit,
    action: @Composable () -> Unit,
    showWeightField: Boolean = true,
) {
    AppBottomSheet(
        onDismissRequest = actions.onDismiss,
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
                if (showWeightField) {
                    NumberInput(
                        value = calculator.draft.weightText,
                        onValueChange = actions.onWeight,
                        label = weightLabel,
                        modifier = Modifier.fillMaxWidth().testTag("${tag}_weight_field"),
                        maxDecimals = WEIGHT_MAX_DECIMALS,
                        maxIntegerDigits = WEIGHT_MAX_INTEGER_DIGITS,
                    )
                }
                results()
            }
            action()
        }
    }
}
