package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme

/** Info sheet (R-07) with why the coach picked an exercise; the title is the exercise name. */
@Composable
internal fun ExerciseReasonSheet(title: String, reason: String, onDismiss: () -> Unit) {
    AppInfoSheet(title = title, onDismiss = onDismiss, testTagPrefix = "block_preview_reason_sheet") {
        Text(
            text = reason,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("block_preview_reason_text"),
        )
    }
}
