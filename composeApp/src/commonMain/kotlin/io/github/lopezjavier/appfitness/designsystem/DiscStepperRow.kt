package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * One row of the disc loader: the disc [label] ("25 kg") on the left and a stepper with the number of discs on the
 * right. The caller supplies the accessibility texts so the unit and wording stay a screen concern. Decrement is
 * disabled at 0 and increment at [maxCount]. Tags: `<tag>` (row), `<tag>_decrement`, `<tag>_increment`, `<tag>_value`.
 */
@Composable
@Suppress("LongParameterList") // Label, count, bounds, handler and three accessibility texts of one row.
fun DiscStepperRow(
    label: String,
    count: Int,
    maxCount: Int,
    onCountChange: (Int) -> Unit,
    decrementContentDescription: String,
    incrementContentDescription: String,
    stateDescription: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).testTag(tag),
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
            valueText = count.toString(),
            onDecrement = { onCountChange(count - 1) },
            onIncrement = { onCountChange(count + 1) },
            decrementEnabled = count > 0,
            incrementEnabled = count < maxCount,
            decrementContentDescription = decrementContentDescription,
            incrementContentDescription = incrementContentDescription,
            stateDescription = stateDescription,
            testTagPrefix = tag,
        )
    }
}
