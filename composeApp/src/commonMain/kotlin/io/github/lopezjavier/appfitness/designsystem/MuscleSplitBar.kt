@file:Suppress("MatchingDeclarationName") // The file holds MuscleSplitBar and its defaults.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [MuscleSplitBar]. */
object MuscleSplitBarDefaults {
    val BarHeight: Dp = 8.dp
}

/**
 * One row of the muscle split of a workout: the group [label] on the left, [valueText] ("12 series · 40 %") on the
 * right and a decorative bar filled to [fraction] (0..1) in the training colour. The whole row is one node whose
 * state description is [description].
 */
@Composable
fun MuscleSplitBar(
    label: String,
    valueText: String,
    fraction: Float,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { stateDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
            Text(valueText, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textSecondary)
        }
        Box(
            Modifier.fillMaxWidth().height(MuscleSplitBarDefaults.BarHeight).clearAndSetSemantics {}
                .clip(AppShapes.full)
                .background(AppTheme.colors.surfaceOverlay),
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(AppShapes.full)
                    .background(AppTheme.colors.athleticCyan),
            )
        }
    }
}

@Preview
@Composable
private fun MuscleSplitBarPreview() {
    AppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            MuscleSplitBar("Pecho", "12 series · 40 %", 0.4f, "Pecho: 12 series, 40 %")
            MuscleSplitBar("Espalda", "6 series · 20 %", 0.2f, "Espalda: 6 series, 20 %")
        }
    }
}
