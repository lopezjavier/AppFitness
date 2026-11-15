package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing

/** Past the target the bar's scale grows to the done figure, never beyond twice the target (the marker stays clear). */
private const val MAX_OVER_SCALE = 2f
private val BarHeight = Spacing.sm

/**
 * One goal row of the Trainer root (05 §6.0g), shared by Calorías, Cardio and Pasos al día: the [label] at the start,
 * the exact "[value]" ("2.122 de 2.507 kcal") at the end, and an 8 dp `athleticCyan` bar below filled to [ratio]. [pace]
 * draws the "due by now" tick while the goal is open; past the target the bar fills completely and the tick marks the
 * target at 1 / ratio. Always cyan: no colour by threshold, no state word, no glyph (05 §6.0g).
 * A null [ratio] means no data: [value] reads muted and no bar is drawn.
 */
@Suppress("LongParameterList") // One bar row: each figure is its own slot.
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun GoalBarRow(
    label: String,
    value: String,
    ratio: Float?,
    pace: Float?,
    stagger: Int,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Label and value share one line; at large font sizes the value wraps under the label instead of colliding.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            Text(
                text = value,
                style = AppTheme.typography.bodyMedium,
                fontWeight = if (ratio == null) FontWeight.Normal else FontWeight.W600,
                color = if (ratio == null) AppTheme.colors.textTertiary else AppTheme.colors.textPrimary,
                modifier = Modifier.align(Alignment.CenterVertically).testTag("${tag}_value"),
            )
        }
        if (ratio != null) {
            val over = ratio > 1f
            val scale = ratio.coerceIn(1f, MAX_OVER_SCALE)
            AppLinearProgress(
                fraction = if (over) 1f else ratio,
                stateDescription = "$label, $value",
                fillColor = AppTheme.colors.athleticCyan,
                barHeight = BarHeight,
                staggerIndex = stagger,
                markerFraction = if (over) 1f / scale else pace?.takeIf { it > 0f && it < 1f },
                modifier = Modifier.testTag("${tag}_bar"),
            )
        }
    }
}
