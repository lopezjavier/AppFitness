package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.history_row_cd
import io.github.lopezjavier.appfitness.composeapp.resources.history_row_sets
import io.github.lopezjavier.appfitness.designsystem.WorkoutRow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A completed workout of [HistoryViewModel] / the calendar's day as a design-system [WorkoutRow]. */
@Composable
internal fun HistoryWorkoutRow(row: HistoryRow, onClick: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    val date = row.label.text()
    val duration = formatElapsed(row.durationSeconds)
    val volume = volumeText(row.volumeKg)
    WorkoutRow(
        name = row.name,
        dateText = date,
        durationText = duration,
        volumeText = volume,
        setsText = pluralStringResource(Res.plurals.history_row_sets, row.completedSets, row.completedSets),
        onClick = onClick,
        contentDescription = stringResource(Res.string.history_row_cd, date, row.name, duration, volume),
        modifier = modifier,
        tag = tag,
    )
}
