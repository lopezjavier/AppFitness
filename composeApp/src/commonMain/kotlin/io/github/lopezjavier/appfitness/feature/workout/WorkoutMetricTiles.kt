package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_timer
import io.github.lopezjavier.appfitness.composeapp.resources.px_live_sets_short
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.summary_duration
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.composeapp.resources.unit_tonne
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
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
                accent = AppTheme.colors.athleticCyan,
                icon = Res.drawable.ic_timer,
            )
        }
        add { tileModifier ->
            MetricTile(
                label = stringResource(Res.string.px_live_sets_short),
                value = AppNumberFormat.integer(completedSets),
                modifier = tileModifier.testTag("${tagPrefix}_sets"),
                accent = AppTheme.colors.neonLime,
                icon = Res.drawable.ic_fitness_center,
            )
        }
        if (volume != null) {
            add { tileModifier ->
                MetricTile(
                    label = stringResource(Res.string.session_detail_volume_label),
                    value = volume.first,
                    modifier = tileModifier.testTag("${tagPrefix}_volume"),
                    unit = volume.second,
                    accent = AppTheme.colors.energyAmber,
                    icon = Res.drawable.ic_bar_chart,
                )
            }
        }
    }
    MetricTileRow(tiles, modifier)
}
