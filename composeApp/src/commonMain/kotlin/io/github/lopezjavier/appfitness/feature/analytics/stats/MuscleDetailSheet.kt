@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.stats_close
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sheet_empty
import io.github.lopezjavier.appfitness.composeapp.resources.stats_sheet_subtitle
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.feature.exercises.label
import org.jetbrains.compose.resources.stringResource

private val SheetSkeletonHeight = Spacing.xl * 4

/** Read-only detail of a muscle over the last 7 days: its sets and the exercises that worked it. */
@Composable
internal fun MuscleDetailSheet(
    group: MuscleGroup,
    detail: SectionState<MuscleDetail>,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onClose, modifier = modifier, testTagPrefix = "stats_muscle_sheet") {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(group.label),
                        style = AppTheme.typography.titleLarge,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.semantics { heading() }.testTag("stats_muscle_sheet_title"),
                    )
                    (detail as? SectionState.Content)?.let {
                        Text(
                            text = stringResource(Res.string.stats_sheet_subtitle, setsText(it.data.sets)),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.testTag("stats_muscle_sheet_subtitle"),
                        )
                    }
                }
                AppIconButton(
                    contentDescription = stringResource(Res.string.stats_close),
                    icon = Res.drawable.ic_close,
                    testTag = "stats_muscle_sheet_close",
                    onClick = onClose,
                )
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
                            ExerciseRow(
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
