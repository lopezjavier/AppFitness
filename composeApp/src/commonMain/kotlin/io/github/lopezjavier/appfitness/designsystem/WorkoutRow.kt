package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private const val STATS_SEPARATOR = " · "

/**
 * A completed workout in a list (R-16): [name] as the headline (`titleMedium`, one line), [dateText] ("lun, 24 oct")
 * as the supporting line and one stats line "52 min · 8.400 kg · 18 series" built from [durationText], [volumeText]
 * and [setsText]. The trailing slot stays empty. One tappable target of at least 48 dp that reads as the single
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
        headlineStyle = AppTheme.typography.titleMedium,
        headlineMaxLines = 1,
        supporting = dateText,
        supportingSecondary = listOf(durationText, volumeText, setsText).joinToString(STATS_SEPARATOR),
        onClick = onClick,
        modifier = modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
    )
}
