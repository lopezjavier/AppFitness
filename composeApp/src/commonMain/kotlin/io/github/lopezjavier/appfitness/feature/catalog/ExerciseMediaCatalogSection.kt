package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_exercise_media
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_animation_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.ExerciseAnimation
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnail
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnailDefaults
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the exercise thumbnail and animation (T-042). Without images both show their fallbacks. */
internal fun LazyListScope.exerciseMediaItems() {
    item { ExerciseMediaSection() }
}

@Composable
private fun ExerciseMediaSection() {
    CatalogSection(stringResource(Res.string.catalog_section_exercise_media), "catalog_section_exercise_media") {
        AppCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                ExerciseThumbnail(
                    imageUri = null,
                    size = ExerciseThumbnailDefaults.RowSize,
                    testTagPrefix = "catalog_exercise_placeholder",
                )
                ExerciseThumbnail(
                    imageUri = "catalog://missing-image",
                    size = ExerciseThumbnailDefaults.RowSize,
                    testTagPrefix = "catalog_exercise_broken",
                )
            }
            ExerciseAnimation(
                startUri = "catalog://missing-start",
                peakUri = "catalog://missing-peak",
                animationsEnabled = LocalAnimationsEnabled.current,
                testTagPrefix = "catalog_exercise",
                contentDescription =
                stringResource(Res.string.exercise_detail_animation_cd, stringResource(Res.string.exercise_unnamed)),
                modifier = Modifier.widthIn(max = ExerciseLayoutDefaults.AnimationMaxWidth),
            )
        }
    }
}
