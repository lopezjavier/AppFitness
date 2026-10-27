package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.NumberInput
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

/** The "?" slot of a calculator sheet header. */
@Composable
internal fun CalculatorHelpButton(title: String, help: CalculatorHelp) {
    HelpButton(
        contentDescription = help.contentDescription,
        title = title,
        body = help.body,
        testTag = help.testTag,
    )
}

/** The working-weight field shared by both calculators (the disc loader has none). */
@Composable
internal fun CalculatorWeightField(
    tag: String,
    weightLabel: String,
    calculator: CalculatorUiState,
    actions: CalculatorActions,
) {
    NumberInput(
        value = calculator.draft.weightText,
        onValueChange = actions.onWeight,
        label = weightLabel,
        modifier = Modifier.fillMaxWidth().testTag("${tag}_weight_field"),
        maxDecimals = WEIGHT_MAX_DECIMALS,
        maxIntegerDigits = WEIGHT_MAX_INTEGER_DIGITS,
    )
}
