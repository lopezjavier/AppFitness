package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.live_exercise_done_cd
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnail
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The exercise picture (placeholder for a custom exercise) in the 32 dp logo footprint. Once every set is done a
 * small `neonLime` check sits inside its bottom end, described as "Ejercicio completado" (R-41: not colour alone).
 */
@Composable
internal fun ExerciseHeaderVisual(exercise: LiveExercise) {
    val position = exercise.position
    val allDone = exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }
    Box(Modifier.size(IconBadgeDefaults.SmallSize).testTag("live_exercise_${position}_header_visual")) {
        ExerciseThumbnail(
            imageUri = exercise.imageKey?.let(LocalExerciseMediaUris.current::start),
            size = IconBadgeDefaults.SmallSize,
            testTagPrefix = "live_exercise_$position",
        )
        if (allDone) {
            val description = stringResource(Res.string.live_exercise_done_cd)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(Spacing.lg)
                    .background(AppTheme.colors.neonLime, CircleShape)
                    .semantics { contentDescription = description }
                    .testTag("live_exercise_${position}_done"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                    tint = AppTheme.colors.onNeonLime,
                    modifier = Modifier.size(Spacing.md),
                )
            }
        }
    }
}
