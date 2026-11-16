package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.summary_duration
import io.github.lopezjavier.appfitness.composeapp.resources.summary_sets
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.composeapp.resources.unit_tonne
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.MetricTileRow
import org.jetbrains.compose.resources.stringResource

/**
 * One row of `MetricTile`s for a finished workout (C-80): duration, completed sets and, when it has one, the volume.
 * Tags are `<tagPrefix>_duration`, `_sets` and `_volume`.
 */
@Composable
internal fun WorkoutMetricTiles(
    durationSeconds: Long,
    completedSets: Int,
    volumeKg: Double?,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    val (duration, durationUnit) = durationMetric(durationSeconds)
    val volume = volumeKg?.let {
        AppNumberFormat.volume(it, stringResource(Res.string.unit_kg), stringResource(Res.string.unit_tonne))
    }
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        add { tileModifier ->
            MetricTile(
                label = stringResource(Res.string.summary_duration),
                value = duration,
                modifier = tileModifier.testTag("${tagPrefix}_duration"),
                unit = durationUnit,
            )
        }
        add { tileModifier ->
            MetricTile(
                label = stringResource(Res.string.summary_sets),
                value = AppNumberFormat.integer(completedSets),
                modifier = tileModifier.testTag("${tagPrefix}_sets"),
            )
        }
        if (volume != null) {
            add { tileModifier ->
                MetricTile(
                    label = stringResource(Res.string.session_detail_volume_label),
                    value = volume.first,
                    modifier = tileModifier.testTag("${tagPrefix}_volume"),
                    unit = volume.second,
                )
            }
        }
    }
    MetricTileRow(tiles, modifier)
}
