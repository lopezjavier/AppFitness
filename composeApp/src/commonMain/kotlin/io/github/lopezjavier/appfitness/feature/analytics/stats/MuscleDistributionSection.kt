package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.stats_body_description
import io.github.lopezjavier.appfitness.composeapp.resources.stats_body_empty
import io.github.lopezjavier.appfitness.composeapp.resources.stats_body_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_chart_empty
import io.github.lopezjavier.appfitness.composeapp.resources.stats_distribution_title
import io.github.lopezjavier.appfitness.composeapp.resources.stats_heat_less
import io.github.lopezjavier.appfitness.composeapp.resources.stats_heat_more
import io.github.lopezjavier.appfitness.composeapp.resources.stats_radar_cd_item
import io.github.lopezjavier.appfitness.composeapp.resources.stats_radar_cd_previous
import io.github.lopezjavier.appfitness.composeapp.resources.stats_radar_current
import io.github.lopezjavier.appfitness.composeapp.resources.stats_radar_previous
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.BodyHeatLegend
import io.github.lopezjavier.appfitness.designsystem.BodyHeatmap
import io.github.lopezjavier.appfitness.designsystem.BodyRegion
import io.github.lopezjavier.appfitness.designsystem.RadarChart
import io.github.lopezjavier.appfitness.designsystem.RadarLegend
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.MuscleGroupSets
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import org.jetbrains.compose.resources.stringResource

private val RadarHeight = Spacing.xl * 12
private val BodyHeight = Spacing.xl * 14

/** Radar "Actual" vs "Periodo anterior" of the frame (the previous series is absent for "Todo"). */
@Composable
internal fun RadarCard(radar: SectionState<MuscleRadar>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    StatsCard(stringResource(Res.string.stats_distribution_title), modifier) {
        StatsBlock(radar, StatsSection.Radar, RadarHeight, onRetry) { data ->
            val axes = data.current.map { stringResource(it.muscleGroup.label) }
            val description = radarDescription(axes, data)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                RadarLegend(
                    currentLabel = stringResource(Res.string.stats_radar_current),
                    previousLabel = data.previous?.let { stringResource(Res.string.stats_radar_previous) },
                    modifier = Modifier.testTag("stats_radar_legend"),
                    currentModifier = Modifier.testTag("stats_radar_legend_current"),
                    previousModifier = Modifier.testTag("stats_radar_legend_previous"),
                )
                RadarChart(
                    axes = axes,
                    current = data.current.map { it.sets },
                    previous = data.previous?.map { it.sets },
                    description = description,
                    modifier = Modifier.fillMaxWidth().testTag("stats_radar"),
                )
                if (data.current.all { it.sets <= 0.0 }) {
                    StatsHint(stringResource(Res.string.stats_chart_empty), Modifier.testTag("stats_radar_empty"))
                }
            }
        }
    }
}

@Composable
private fun radarDescription(axes: List<String>, data: MuscleRadar): String {
    val current = data.current.mapIndexed { i, v ->
        stringResource(Res.string.stats_radar_cd_item, axes[i], setsText(v.sets))
    }.joinToString(", ")
    val previous = data.previous?.let { list ->
        list.mapIndexed { i, v -> stringResource(Res.string.stats_radar_cd_item, axes[i], setsText(v.sets)) }
            .joinToString(", ")
    }
    return listOfNotNull(current, previous?.let { stringResource(Res.string.stats_radar_cd_previous, it) })
        .joinToString(". ")
}

/** Body map of the last 7 days with a visible list of every muscle as the reliable tap path. */
@Composable
internal fun BodyMapCard(
    bodyMap: SectionState<List<MuscleGroupSets>>,
    onRetry: () -> Unit,
    onMuscleClick: (MuscleGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatsCard(stringResource(Res.string.stats_body_title), modifier) {
        StatsBlock(bodyMap, StatsSection.Body, BodyHeight, onRetry) { rows ->
            val regions = rows.map { row ->
                val name = stringResource(row.muscleGroup.label)
                BodyRegion(
                    group = row.muscleGroup,
                    sets = row.sets,
                    description = stringResource(Res.string.stats_body_description, name, setsText(row.sets)),
                    valueText = formatSetsNumber(row.sets),
                    tag = "stats_body_muscle_${row.muscleGroup.tagName()}",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                BodyHeatmap(regions, onMuscleClick, tagPrefix = "stats_body")
                BodyHeatLegend(
                    stringResource(Res.string.stats_heat_less),
                    stringResource(Res.string.stats_heat_more),
                    Modifier.testTag("stats_body_legend"),
                )
                if (rows.all { it.sets <= 0.0 }) {
                    StatsHint(stringResource(Res.string.stats_body_empty), Modifier.testTag("stats_body_empty"))
                }
                MuscleList(rows.sortedByDescending { it.sets }, onMuscleClick)
            }
        }
    }
}

@Composable
private fun MuscleList(rows: List<MuscleGroupSets>, onMuscleClick: (MuscleGroup) -> Unit) {
    Column {
        rows.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                pair.forEach { row -> MuscleListRow(row, { onMuscleClick(row.muscleGroup) }, Modifier.weight(1f)) }
                if (pair.size == 1) Row(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun MuscleListRow(row: MuscleGroupSets, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(row.muscleGroup.label)
    val sets = setsText(row.sets)
    Row(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, $sets"
            }
            .testTag("stats_body_list_${row.muscleGroup.tagName()}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(sets, style = AppTheme.typography.labelTechnical, color = AppTheme.colors.textSecondary)
    }
}
