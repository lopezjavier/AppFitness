package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry of [DiaryEnergyScale]. */
object DiaryEnergyScaleDefaults {
    val CellHeight: Dp = 64.dp
    const val IDLE_ALPHA = 0.14f
    const val DISABLED_ALPHA = 0.5f
}

/**
 * A graphic 1..N scale: one tall cell per option, tinted from amber (low) to lime (high), the chosen one lit with the
 * full ramp. A single-select `Role.RadioButton` per cell with the tag and description of its [AppOption]; [lowLabel]
 * and [highLabel] name the two ends.
 */
@Composable
fun DiaryEnergyScale(
    options: List<AppOption>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    lowLabel: String,
    highLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            Modifier.fillMaxWidth().alpha(
                if (enabled) 1f else DiaryEnergyScaleDefaults.DISABLED_ALPHA,
            ).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            options.forEachIndexed { index, option ->
                val tone = lerp(
                    colors.energyAmber,
                    colors.neonLime,
                    index / (options.size - 1).coerceAtLeast(1).toFloat(),
                )
                val selected = index == selectedIndex
                val shape = AppShapes.md
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = DiaryEnergyScaleDefaults.CellHeight)
                        .clip(shape)
                        .background(
                            if (selected) AppPaint.vertical(tone) else AppPaint.tintSurface(tone),
                        )
                        .border(
                            if (selected) AppBorder.focus else AppBorder.thin,
                            if (selected) tone else tone.copy(alpha = 0.35f),
                            shape,
                        )
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                        .semantics { option.contentDescription?.let { contentDescription = it } }
                        .testTag(option.testTag),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = AppTheme.typography.headlineMetric.copy(
                            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
                        ),
                        color = if (selected) colors.surfaceBase else colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs)) {
            Text(
                lowLabel,
                style = AppTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                highLabel,
                style = AppTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
