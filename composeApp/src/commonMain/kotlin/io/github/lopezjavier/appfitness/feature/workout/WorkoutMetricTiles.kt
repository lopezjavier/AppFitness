package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.session_detail_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.summary_duration
import io.github.lopezjavier.appfitness.composeapp.resources.summary_sets
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

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
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        MetricTile(
            label = stringResource(Res.string.summary_duration),
            value = duration,
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("${tagPrefix}_duration"),
            unit = durationUnit,
        )
        MetricTile(
            label = stringResource(Res.string.summary_sets),
            value = AppNumberFormat.integer(completedSets),
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("${tagPrefix}_sets"),
        )
        if (volumeKg != null) {
            MetricTile(
                label = stringResource(Res.string.session_detail_volume_label),
                value = AppNumberFormat.integer(volumeKg.roundToInt()),
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("${tagPrefix}_volume"),
                unit = stringResource(Res.string.unit_kg),
            )
        }
    }
}

