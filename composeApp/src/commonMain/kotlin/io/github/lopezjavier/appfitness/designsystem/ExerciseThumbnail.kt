@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults objects.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_exercise_placeholder
import org.jetbrains.compose.resources.painterResource

/** Geometry and tags of [ExerciseThumbnail]. */
object ExerciseThumbnailDefaults {
    /** Thumbnail size in a list row. */
    val RowSize: Dp = 56.dp

    /** Placeholder glyph size relative to the thumbnail size. */
    const val PLACEHOLDER_FRACTION = 0.5f

    fun imageTag(prefix: String): String = "${prefix}_thumbnail_image"

    fun placeholderTag(prefix: String): String = "${prefix}_thumbnail_placeholder"
}

/** Layout constants of the exercise screens (list rows, wide layouts). */
object ExerciseLayoutDefaults {
    /** Minimum height of a list row (thumbnail plus padding). */
    val RowMinHeight: Dp = 64.dp

    /** Content cap on wide windows. */
    val ContentMaxWidth: Dp = 840.dp

    /** Cap of the demonstration animation. */
    val AnimationMaxWidth: Dp = 360.dp

    /** List columns on wide windows. */
    const val WIDE_COLUMNS = 2
}

/**
 * Square exercise image (the start pose) cropped into [size], or the dumbbell placeholder when there is no
 * [imageUri], while it loads and if it fails. [contentDescription] null or empty makes it decorative (list rows).
 * Children are tagged `<testTagPrefix>_thumbnail_image` and `<testTagPrefix>_thumbnail_placeholder`.
 */
@Composable
fun ExerciseThumbnail(
    imageUri: String?,
    size: Dp,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = AppTheme.colors
    var loaded by remember(imageUri) { mutableStateOf(false) }
    val semanticsModifier =
        if (contentDescription.isNullOrEmpty()) {
            Modifier.clearAndSetSemantics {}
        } else {
            Modifier.semantics { this.contentDescription = contentDescription }
        }
    Box(
        modifier =
        modifier
            .size(size)
            .clip(AppShapes.md)
            .background(colors.surfaceOverlay)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Icon(
                painter = painterResource(Res.drawable.ic_exercise_placeholder),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier =
                Modifier
                    .size(size * ExerciseThumbnailDefaults.PLACEHOLDER_FRACTION)
                    .testTag(ExerciseThumbnailDefaults.placeholderTag(testTagPrefix)),
            )
        }
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().testTag(ExerciseThumbnailDefaults.imageTag(testTagPrefix)),
            )
        }
    }
}

@Preview
@Composable
private fun ExerciseThumbnailPreview() {
    AppTheme { ExerciseThumbnail(imageUri = null, size = ExerciseThumbnailDefaults.RowSize, testTagPrefix = "preview") }
}
