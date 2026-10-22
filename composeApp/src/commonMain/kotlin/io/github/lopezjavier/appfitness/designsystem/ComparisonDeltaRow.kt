package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

/**
 * One line of a comparison: the [label], the [valuesText] ("75,6 → 74,2") and the signed [deltaText] ("−1,4 kg").
 * The delta is neutral (`textPrimary`): losing weight is not good or bad by itself. [deltaDescription] is the spoken
 * form ("1,4 kg menos"); the row is one accessibility node. The caller's [modifier] lands on the root.
 */
@Composable
fun ComparisonDeltaRow(
    label: String,
    valuesText: String,
    deltaText: String,
    deltaDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $valuesText, $deltaDescription" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = label.uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
            )
            Text(text = valuesText, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
        Text(text = deltaText, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textPrimary)
    }
}

@Preview
@Composable
private fun ComparisonDeltaRowPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.lg)) {
            ComparisonDeltaRow("Peso", "75,6 → 74,2 kg", "−1,4 kg", "1,4 kg menos")
            ComparisonDeltaRow("Grasa corporal", "20,1 → 19,4 %", "−0,7 pt", "0,7 puntos menos")
        }
    }
}
