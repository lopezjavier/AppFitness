package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_score
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import org.jetbrains.compose.resources.stringResource

/** Fraction of the full score bar. */
private const val FULL_PERCENT = 100f

/** "Rutina completa al X %" with its bar, shared by the builder and the folder block; [tag] tags the text. */
@Composable
internal fun BuilderScoreBar(percent: Int, tag: String) {
    val scoreText = stringResource(Res.string.block_builder_score, percent)
    androidx.compose.material3.Text(
        text = scoreText,
        style = AppTheme.typography.titleMedium,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.testTag(tag).semantics { stateDescription = scoreText },
    )
    AppLinearProgress(percent / FULL_PERCENT, scoreText)
}
