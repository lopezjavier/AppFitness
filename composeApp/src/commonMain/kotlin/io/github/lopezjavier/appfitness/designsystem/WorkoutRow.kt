package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.composeapp.resources.ic_timer
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * A completed workout in a list (R-16): a card with a lit icon badge, [name] as the headline (`titleMedium`, one
 * line), [dateText] ("lun, 24 oct") as the supporting line and the figures [durationText], [volumeText] (when there
 * is one) and [setsText] as small stats with a glyph each. The card is one tappable target of at least 48 dp that
 * reads as the single sentence [contentDescription]; the caller passes the already formatted texts and owns the [tag].
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow lets the stats wrap at large font scales.
@Suppress("LongParameterList") // One slot per figure of the row.
@Composable
fun WorkoutRow(
    name: String,
    dateText: String,
    durationText: String,
    volumeText: String?,
    setsText: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tag: String = "workout_row",
) {
    AppCard(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Res.drawable.ic_fitness_center, AppTheme.colors.athleticCyan)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = dateText, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    WorkoutStat(Res.drawable.ic_timer, durationText)
                    volumeText?.let { WorkoutStat(Res.drawable.ic_monitor_weight, it) }
                    WorkoutStat(Res.drawable.ic_bar_chart, setsText)
                }
            }
            Icon(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                tint = AppTheme.colors.textTertiary,
                modifier = Modifier.size(Spacing.xl),
            )
        }
    }
}

@Composable
private fun WorkoutStat(icon: DrawableResource, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppPaint.ramp(AppTheme.colors.athleticCyan).bright,
            modifier = Modifier.size(Spacing.lg),
        )
        Text(text = text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textPrimary, maxLines = 1)
    }
}
