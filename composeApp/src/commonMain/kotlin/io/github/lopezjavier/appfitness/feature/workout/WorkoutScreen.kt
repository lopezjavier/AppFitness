package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.composeapp.resources.workout_exercise_library
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/**
 * Work-in-progress workout tab until its epic replaces it. It carries a provisional "Biblioteca de ejercicios"
 * button (T-042) that opens the exercise library; T-043 removes it.
 */
@Composable
fun WorkoutScreen(modifier: Modifier = Modifier, onOpenExerciseLibrary: () -> Unit = {}) {
    Box(modifier.fillMaxSize()) {
        WipPlaceholder(
            message = stringResource(Res.string.wip_message),
            footer = stringResource(Res.string.wip_footer),
            testTagPrefix = "workout",
            modifier = Modifier.testTag("workout_screen"),
        )
        SecondaryButton(
            text = stringResource(Res.string.workout_exercise_library),
            onClick = onOpenExerciseLibrary,
            modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Spacing.screenMargin)
                .testTag("workout_exercise_library_button"),
        )
    }
}
