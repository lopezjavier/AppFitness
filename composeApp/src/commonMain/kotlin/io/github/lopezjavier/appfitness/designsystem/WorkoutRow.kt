package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * A completed workout in a list (R-16): [name] as the headline, [dateText] ("lun, 24 oct") as the supporting line and
 * the duration, volume and sets as trailing figures. One tappable target of at least 48 dp that reads as the single
 * sentence [contentDescription]; the caller passes the already formatted texts and owns the [tag].
 */
@Suppress("LongParameterList") // One slot per figure of the row.
@Composable
fun WorkoutRow(
    name: String,
    dateText: String,
    durationText: String,
    volumeText: String,
    setsText: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tag: String = "workout_row",
) {
    AppListRow(
        headline = name,
        supporting = dateText,
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(durationText, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
                Text(volumeText, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.athleticCyan)
                Text(setsText, style = AppTheme.typography.labelNumeric, color = AppTheme.colors.textSecondary)
            }
        },
        onClick = onClick,
        modifier = modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    )
}
