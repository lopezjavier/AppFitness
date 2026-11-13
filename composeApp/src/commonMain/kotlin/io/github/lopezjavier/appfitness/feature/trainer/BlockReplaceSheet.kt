package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_preview_replace_cardio_note
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_reset
import io.github.lopezjavier.appfitness.composeapp.resources.block_replace_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_inbox
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.exercises.SimilarExerciseItem
import io.github.lopezjavier.appfitness.feature.exercises.tagKey
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3

/**
 * Picker of the alternatives of one exercise of the block (ADR-83, R-12): tapping a row applies it and the sheet
 * closes; the thumbnail opens the exercise details. A changed slot also offers "Volver al ejercicio original" on top.
 * Offline reloads by itself once the connection is back (R-30).
 */
@Composable
@Suppress("LongMethod") // One branch per state of the alternatives.
internal fun BlockReplaceSheet(
    state: BlockPreviewUiState.Preview,
    isOnline: Flow<Boolean>,
    onEvent: (BlockPreviewEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val changed = state.replaceSlot in state.replacements
    AppPickerSheet(
        title = stringResource(Res.string.block_replace_title),
        onDismiss = { onEvent(BlockPreviewEvent.CloseReplace) },
        testTagPrefix = "block_preview_replace_sheet",
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin)) {
            if (changed) {
                AppListRow(
                    headline = stringResource(Res.string.block_replace_reset),
                    modifier = Modifier.testTag("block_preview_replace_reset"),
                    onClick = {
                        haptics.confirm()
                        onEvent(BlockPreviewEvent.ClearReplacement)
                    },
                )
            }
            when (val alternatives = state.alternatives) {
                AlternativesState.Loading -> Column(
                    Modifier.fillMaxWidth().testTag("block_preview_replace_loading"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) }
                }

                AlternativesState.Empty -> Centered {
                    EmptyState(
                        icon = Res.drawable.ic_inbox,
                        message = stringResource(Res.string.block_replace_empty),
                        testTagPrefix = "block_preview_replace_empty",
                    )
                }

                AlternativesState.Offline -> Centered {
                    OfflineState(
                        isOnline = isOnline,
                        onReload = { onEvent(BlockPreviewEvent.LoadAlternatives) },
                        modifier = Modifier.testTag("block_preview_replace_offline"),
                    )
                }

                AlternativesState.Error -> Centered {
                    ErrorState(
                        message = stringResource(Res.string.block_replace_error),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(BlockPreviewEvent.LoadAlternatives)
                        },
                        testTagPrefix = "block_preview_replace_error",
                        actionTestTag = "block_preview_replace_retry",
                    )
                }

                is AlternativesState.Content -> {
                    alternatives.rows.forEach { row ->
                        SimilarExerciseItem(
                            row = row,
                            rowTag = "block_preview_replace_option_${row.tagKey}",
                            percentTag = "block_preview_replace_percent_${row.tagKey}",
                            favoriteTag = "block_preview_replace_favorite_${row.tagKey}",
                            onOpen = { onOpenExercise(row.exercise.id) },
                            onSelect = {
                                haptics.confirm()
                                onEvent(BlockPreviewEvent.PickReplacement(row.exercise.id))
                            },
                            showPercent = !state.isCardioSwap,
                        )
                    }
                    if (state.isCardioSwap) {
                        Text(
                            text = stringResource(Res.string.block_preview_replace_cardio_note),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = Spacing.md)
                                .testTag("block_preview_replace_cardio_note"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), contentAlignment = Alignment.Center) { content() }
}
