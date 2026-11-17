package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sheet_empty
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sheet_subtitle
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.stringResource

private val SheetSkeletonHeight = Spacing.xl * 4

/**
 * Read-only detail of a muscle over the last 7 days (Info archetype, R-07): the muscle is the title, then its sets and
 * the exercises that worked it. Tags: `stats_muscle_sheet`, `_title`, `_close` (archetype), `_subtitle`, `_empty`.
 */
@Composable
internal fun MuscleDetailSheet(
    group: MuscleGroup,
    detail: SectionState<MuscleDetail>,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppInfoSheet(
        title = stringResource(group.label),
        onDismiss = onClose,
        modifier = modifier,
        testTagPrefix = "stats_muscle_sheet",
    ) {
        Column(Modifier.padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            (detail as? SectionState.Content)?.let {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        Res.drawable.ic_fitness_center,
                        AppTheme.colors.athleticCyan,
                        size = IconBadgeDefaults.LargeSize,
                        iconSize = IconBadgeDefaults.LargeIconSize,
                    )
                    Column {
                        Text(
                            text = formatSetsNumber(it.data.sets),
                            style = AppTheme.typography.displayHero,
                            color = AppTheme.colors.athleticCyan,
                        )
                        StatsHint(
                            text = stringResource(Res.string.stats_sheet_subtitle, setsText(it.data.sets)),
                            modifier = Modifier.testTag("stats_muscle_sheet_subtitle"),
                        )
                    }
                }
            }
            StatsBlock(detail, StatsSection.Sheet, SheetSkeletonHeight, onRetry) { data ->
                if (data.exercises.isEmpty()) {
                    StatsHint(
                        stringResource(Res.string.stats_sheet_empty),
                        Modifier.testTag("stats_muscle_sheet_empty"),
                    )
                } else {
                    Column {
                        data.exercises.forEach {
                            StatsExerciseRow(
                                it,
                                "stats_muscle_sheet_exercise_${it.exerciseId}",
                                { onOpenExercise(it.exerciseId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
