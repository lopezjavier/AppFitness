package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_attribution
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_no_instructions
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_tips
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_step_number
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ExerciseDetail
import org.jetbrains.compose.resources.stringResource

/**
 * "Cómo hacerlo" tab: the numbered Spanish steps, the tips and the RepDB attribution. An exercise without library
 * data (custom exercises) shows a centred message and no attribution.
 */
@Composable
internal fun ExerciseHowToTab(
    detail: ExerciseDetail,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val info = detail.info
    if (info == null || (info.instructions.isEmpty() && info.tips.isEmpty())) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(
                message = stringResource(Res.string.exercise_detail_no_instructions),
                testTagPrefix = "exercise_detail_how_to_empty",
            )
        }
        return
    }
    Column(
        modifier
            .widthIn(max = ExerciseLayoutDefaults.ContentMaxWidth)
            .verticalScroll(scrollState)
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        HowToSteps(info.instructions)
        DetailTips(info.tips)
        Text(
            text = stringResource(Res.string.exercise_attribution),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("exercise_detail_attribution"),
        )
    }
}

@Composable
private fun HowToSteps(instructions: List<String>) {
    Column(Modifier.testTag("exercise_detail_how_to"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
