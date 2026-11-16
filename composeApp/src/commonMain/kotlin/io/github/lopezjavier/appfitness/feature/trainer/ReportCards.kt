package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_review_sessions_header
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_cd
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_headline
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.BarListRow
import io.github.lopezjavier.appfitness.designsystem.BarListRowDefaults
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.MetricTileRow
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.stringResource

/**
 * The "Entrenos" card of both reports (C-102): the label, "3 de 4" as the headline, an optional [percent] next to it
 * and one segment per planned workout (a linear bar above 12). Spoken as "3 de 4 entrenos".
 */
@Composable
internal fun SessionsCard(tag: String, completed: Int, planned: Int, percent: String? = null) {
    val spoken = stringResource(Res.string.report_sessions_cd, completed, planned)
    SectionCard(stringResource(Res.string.block_review_sessions_header), testTag = tag) {
        Row(
            Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.report_sessions_headline, completed, planned),
                style = AppTheme.typography.headlineMetric,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("${tag}_headline"),
            )
            if (percent != null) {
                Text(
                    text = percent,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${tag}_percent"),
                )
            }
        }
        SegmentedProgress(
            total = planned,
            done = completed.coerceAtMost(planned),
            stateDescription = spoken,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            testTag = "${tag}_progress",
        )
    }
}

/** The sets per muscle group as a bar list (C-102); shared with the end-of-block review. */
@Composable
internal fun GroupsCard(allGroups: List<GroupSets>, tagPrefix: String, header: String) {
    val groups = WeekSummaryText.visibleGroups(allGroups)
    if (groups.isEmpty()) return
    val largest = groups.maxOf { it.sets }
    SectionCard(header, testTag = "${tagPrefix}_section_groups") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            groups.forEachIndexed { index, group ->
                BarListRow(
                    label = stringResource(group.muscleGroup.label),
                    value = WeekSummaryText.sets(group.sets),
                    fraction = BarListRowDefaults.fraction(group.sets, largest),
                    staggerIndex = index,
                    testTag = "${tagPrefix}_group_${group.muscleGroup.name}",
                )
            }
        }
    }
}

/** A plain read-only card for a one-line status (no title). */
@Composable
internal fun ReportNoteCard(tag: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag(tag)) { content() }
}

/** One figure of a [ReportTileGrid]: its tile (a `MetricTile`) and the tag it carries. */
internal class ReportTile(
    val tag: String,
    val label: String,
    val value: String,
    val unit: String? = null,
    val supporting: String? = null,
)

/** Figures as `MetricTile`s, two per row (the 2 x 2 grid R-85 allows for four nutrition figures); equal heights. */
@Composable
internal fun ReportTileGrid(tiles: List<ReportTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        tiles.chunked(2).forEach { pair ->
            MetricTileRow(
                tiles = pair.map { tile ->
                    { m: Modifier ->
                        MetricTile(
                            label = tile.label,
                            value = tile.value,
                            modifier = m.testTag(tile.tag),
                            unit = tile.unit,
                            supporting = tile.supporting?.let {
                                {
                                    Text(
                                        text = it,
                                        style = AppTheme.typography.bodySmall,
                                        color = AppTheme.colors.textSecondary,
                                    )
                                }
                            },
                        )
                    }
                },
                columns = 2,
            )
        }
    }
}
