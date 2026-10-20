package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_attribution
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_animation_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_how_to
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_no_instructions
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_select
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_tips
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_progress_link
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_step_number
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseAnimation
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TextPill
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stateful detail: connects [ExerciseDetailViewModel] to the stateless [ExerciseDetailScreen]. */
@Composable
fun ExerciseDetailRoot(
    exerciseId: String,
    selectMode: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    viewModel: ExerciseDetailViewModel = koinViewModel(key = exerciseId, parameters = { parametersOf(exerciseId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(state, selectMode, onSelect, viewModel::retry, modifier, onOpenProgress)
}

/**
 * Exercise detail (US-09): the looping demonstration, muscle group and equipment, "Cómo hacerlo" and tips, with the
 * RepDB attribution last. In [selectMode] a "Seleccionar" button is pinned at the bottom.
 */
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    selectMode: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().testTag("exercise_detail_screen")) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (state) {
                ExerciseDetailUiState.Loading -> DetailLoading()

                ExerciseDetailUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(
                            message = stringResource(Res.string.exercise_error_message),
                            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
                            testTagPrefix = "exercise_detail_error",
                        )
                    }

                is ExerciseDetailUiState.Loaded -> DetailContent(state.detail, onOpenProgress)
            }
        }
        if (selectMode && state is ExerciseDetailUiState.Loaded) {
            PrimaryButton(
                text = stringResource(Res.string.exercise_detail_select),
                onClick = onSelect,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                    .testTag("exercise_detail_select_button"),
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().widthIn(max = ExerciseLayoutDefaults.AnimationMaxWidth).padding(Spacing.xl))
        SkeletonBox(Modifier.fillMaxWidth(NAME_FRACTION).padding(Spacing.lg))
    }
}

private const val NAME_FRACTION = 0.6f

@Composable
private fun DetailContent(detail: ExerciseDetail, onOpenProgress: () -> Unit) {
    val name = detail.name.ifBlank { stringResource(Res.string.exercise_unnamed) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        Column(
            Modifier
                .widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                    Box(Modifier.weight(1f)) { DetailMedia(detail, name) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                        DetailText(detail, name, onOpenProgress)
                    }
                }
            } else {
                DetailMedia(detail, name)
                DetailText(detail, name, onOpenProgress)
            }
            Text(
                text = stringResource(Res.string.exercise_attribution),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("exercise_detail_attribution"),
            )
        }
    }
}

@Composable
private fun DetailMedia(detail: ExerciseDetail, name: String) {
    val key = detail.exercise.key
    val info = detail.info
    val uris = LocalExerciseMediaUris.current
    val startUri = if (key != null && info != null) uris.start(key) else null
    if (key != null && info != null && startUri != null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            ExerciseAnimation(
                startUri = startUri,
                peakUri = if (info.hasPeak) uris.peak(key) else null,
                animationsEnabled = LocalAnimationsEnabled.current,
                testTagPrefix = "exercise_detail",
                contentDescription = stringResource(Res.string.exercise_detail_animation_cd, name),
                modifier = Modifier.widthIn(max = ExerciseLayoutDefaults.AnimationMaxWidth),
            )
        }
    }
}

@Composable
private fun DetailText(detail: ExerciseDetail, name: String, onOpenProgress: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        DetailHeader(detail, name)
        SecondaryButton(
            text = stringResource(Res.string.exercise_progress_link),
            onClick = onOpenProgress,
            modifier = Modifier.fillMaxWidth().testTag("exercise_detail_progress_link"),
        )
        DetailHowTo(detail.info?.instructions.orEmpty())
        DetailTips(detail.info?.tips.orEmpty())
    }
}

@Composable
private fun DetailHeader(detail: ExerciseDetail, name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = name,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextPill(stringResource(detail.exercise.muscleGroup.label))
            TextPill(stringResource(detail.exercise.equipment.label))
        }
    }
}

@Composable
private fun DetailHowTo(instructions: List<String>) {
    Column(Modifier.testTag("exercise_detail_how_to"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.exercise_detail_how_to),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        if (instructions.isEmpty()) {
            Text(
                text = stringResource(Res.string.exercise_detail_no_instructions),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
            )
        }
        instructions.forEachIndexed { index, step ->
            Text(
                text = stringResource(Res.string.exercise_step_number, index + 1, step),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun DetailTips(tips: List<String>) {
    if (tips.isEmpty()) return
    Column(Modifier.testTag("exercise_detail_tips"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.exercise_detail_tips),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        tips.forEach {
            Text(text = it, style = AppTheme.typography.bodyLarge, color = AppTheme.colors.textSecondary)
        }
    }
}
