package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_similar_empty
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_similar_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_similar_percent_cd
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PercentRing
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 4

/**
 * "Similares" tab (ADR-83): the curated alternatives of the exercise, most alike first, each with thumbnail, name,
 * equipment and a percentage. Tapping a row calls [onOpenExercise]. [isOnline] only drives the automatic reload of
 * the offline state (R-30); [onRetry] reloads after a failure.
 */
@Composable
internal fun ExerciseSimilarTab(
    state: SimilarState,
    isOnline: Boolean,
    onOpenExercise: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth).fillMaxSize()) {
            when (state) {
                SimilarState.Loading -> SimilarLoading()

                SimilarState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        message = stringResource(Res.string.exercise_similar_empty),
                        testTagPrefix = "exercise_detail_similar_empty",
                    )
                }

                SimilarState.Offline -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val online by rememberUpdatedState(isOnline)
                    OfflineState(
                        isOnline = remember { snapshotFlow { online } },
                        onReload = onRetry,
                        modifier = Modifier.testTag("exercise_detail_similar_offline"),
                    )
                }

                SimilarState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.exercise_similar_error),
                        action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
                        testTagPrefix = "exercise_detail_similar_error",
                        actionTestTag = "exercise_detail_similar_retry",
                    )
                }

                is SimilarState.Content -> LazyColumn(
                    Modifier.fillMaxSize().testTag("exercise_detail_similar_list"),
                    contentPadding = PaddingValues(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                ) {
                    items(state.rows, key = { it.exercise.id }) { row ->
                        SimilarExerciseItem(
                            row = row,
                            rowTag = "exercise_detail_similar_row_${row.tagKey}",
                            percentTag = "exercise_detail_similar_percent_${row.tagKey}",
                            favoriteTag = "exercise_similar_favorite_${row.tagKey}",
                            onOpen = { onOpenExercise(row.exercise.id) },
                            modifier = appAnimateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("exercise_detail_similar_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) }
    }
}

/**
 * One alternative as an [ExerciseRow]: thumbnail, name, equipment and the similarity as a [PercentRing] (the whole
 * number inside) read as "87 por ciento de similitud". Tagged [rowTag] and [percentTag]. With [onSelect] the
 * row body picks and the thumbnail opens the details (R-17). [showPercent] false hides the ring (cardio swap).
 */
@Composable
internal fun SimilarExerciseItem(
    row: SimilarExerciseRow,
    rowTag: String,
    percentTag: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (() -> Unit)? = null,
    showPercent: Boolean = true,
    favoriteTag: String? = null,
) {
    val exercise = row.exercise.exercise
    val mediaKey = exercise.key
    val percent = row.percent.toString()
    val percentDescription = stringResource(Res.string.exercise_similar_percent_cd, percent)
    ExerciseRow(
        name = row.exercise.name,
        supporting = stringResource(exercise.equipment.label),
        onOpen = onOpen,
        tagPrefix = rowTag,
        modifier = modifier,
        imageUri = mediaKey?.takeIf { row.exercise.hasMedia }?.let { LocalExerciseMediaUris.current.start(it) },
        trailing = if (showPercent) {
            { PercentRing(row.percent, percentDescription, Modifier.testTag(percentTag)) }
        } else {
            null
        },
        onSelect = onSelect,
        favorite = row.isFavorite,
        favoriteTestTag = favoriteTag,
    )
}

/** Stable id of the row in test tags: the catalogue key, or the id of an exercise without one. */
internal val SimilarExerciseRow.tagKey: String get() = exercise.exercise.key ?: exercise.id
