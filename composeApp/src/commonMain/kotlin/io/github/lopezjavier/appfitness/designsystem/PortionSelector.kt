package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview

/** A named amount offered by [PortionSelector]; [label] is already formatted ("1 huevo (55 g)"). */
@Immutable
data class PortionOption(val id: String, val label: String)

/** Limits of the amount field of [PortionSelector]. */
object PortionSelectorDefaults {
    const val MAX_DECIMALS = 1

    /** Enough for 5000 and a count of portions. */
    const val MAX_INTEGER_DIGITS = 4
}

/**
 * Quantity input of a food: an amount field (1 decimal) and, when the food has named portions, a chip row that
 * switches between the raw base unit ([baseLabel], selected when [selectedPortionId] is null) and each of
 * [portions]; then [amountText] is a count of that portion. [errorText] shows below the field as a live region and
 * marks it as an error (text, not colour only).
 *
 * Tags derive from [testTagPrefix]: `<prefix>_amount`, `<prefix>_portion`, `<prefix>_portion_option_<id>` (`base` for
 * the raw unit) and `<prefix>_error`. The typed text is mirrored locally so every keystroke is applied at once;
 * switching the portion takes the new [amountText]. [amountTrailing], when set, sits after the field in the same row.
 */
@Suppress("LongParameterList") // Amount, portion and presentation inputs of one control.
@Composable
fun PortionSelector(
    amountText: String,
    onAmountChange: (String) -> Unit,
    amountLabel: String,
    baseLabel: String,
    portions: List<PortionOption>,
    selectedPortionId: String?,
    onPortionSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    testTagPrefix: String = "portion",
    amountTrailing: (@Composable () -> Unit)? = null,
) {
    var typed by remember(selectedPortionId) { mutableStateOf(amountText.toDisplay()) }
    // An amount that changes from outside (e.g. a photo estimate) replaces the typed text; the echo of a keystroke
    // (same number) and an unchanged parent value do not.
    var lastAmount by remember { mutableStateOf(amountText) }
    if (amountText != lastAmount) {
        lastAmount = amountText
        if (amountText.toNumber() != typed.toNumber()) typed = amountText.toDisplay()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (portions.isNotEmpty()) {
            val options =
                listOf(AppOption(baseLabel, "${testTagPrefix}_portion_option_base")) +
                    portions.map { AppOption(it.label, "${testTagPrefix}_portion_option_${it.id}") }
            val selected = portions.indexOfFirst { it.id == selectedPortionId }.let { if (it < 0) 0 else it + 1 }
            FilterChipRow(
                options = options,
                selectedIndex = selected,
                onSelect = { index -> onPortionSelect(portions.getOrNull(index - 1)?.id) },
                modifier = Modifier.testTag("${testTagPrefix}_portion"),
            )
        }
        AmountRow(
            value = typed,
            label = amountLabel,
            onValueChange = {
                typed = it
                onAmountChange(it)
            },
            tag = "${testTagPrefix}_amount",
            trailing = amountTrailing,
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        error(errorText)
                    }
                    .testTag("${testTagPrefix}_error"),
            )
        }
    }
}

@Composable
private fun AmountRow(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    tag: String,
    trailing: (@Composable () -> Unit)?,
) {
    if (trailing == null) {
        AmountField(value, label, onValueChange, Modifier.fillMaxWidth().testTag(tag))
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        // The label sits above the input (R-24): bottom-align and lift the trailing control by half the height
        // difference so it is centred on the input box, not on label plus input.
        verticalAlignment = Alignment.Bottom,
    ) {
        AmountField(value, label, onValueChange, Modifier.weight(1f).testTag(tag))
        val lift = (AppTextFieldDefaults.MinHeight - AppTopBarDefaults.NavigationTouchTarget) / 2
        Box(Modifier.padding(bottom = lift)) { trailing() }
    }
}

@Composable
private fun AmountField(value: String, label: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    NumberInput(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        maxDecimals = PortionSelectorDefaults.MAX_DECIMALS,
        maxIntegerDigits = PortionSelectorDefaults.MAX_INTEGER_DIGITS,
        imeAction = ImeAction.Done,
    )
}

private fun String.toDisplay(): String = replace('.', NumberInputDefaults.DECIMAL_SEPARATOR)

private fun String.toNumber(): Double? = replace(NumberInputDefaults.DECIMAL_SEPARATOR, '.').toDoubleOrNull()

@Preview
@Composable
private fun PortionSelectorPreview() {
    AppTheme {
        PortionSelector(
            amountText = "2",
            onAmountChange = {},
            amountLabel = "Número de porciones",
            baseLabel = "Gramos",
            portions = listOf(PortionOption("egg", "1 huevo (55 g)")),
            selectedPortionId = "egg",
            onPortionSelect = {},
        )
    }
}
