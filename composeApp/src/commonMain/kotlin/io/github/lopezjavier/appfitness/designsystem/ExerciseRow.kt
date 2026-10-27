package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Exercise row (R-16, R-18): the thumbnail ([imageUri], the placeholder when null) and the [name] with an optional
 * [supporting] line, on an [AppListRow]. Tapping the thumbnail or the name calls [onOpen] (the exercise details);
 * [trailing] is a separate slot for the caller's own controls. The row is tagged [tagPrefix]; the thumbnail
 * children are `<tagPrefix>_thumbnail_image` and `<tagPrefix>_thumbnail_placeholder`.
 */
@Composable
fun ExerciseRow(
    name: String,
    supporting: String?,
    onOpen: () -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    AppListRow(
        headline = name,
        modifier = modifier.testTag(tagPrefix),
        supporting = supporting,
        leading = {
            ExerciseThumbnail(
                imageUri = imageUri,
                size = ExerciseThumbnailDefaults.RowSize,
                testTagPrefix = tagPrefix,
            )
        },
        trailing = trailing,
        onClick = onOpen,
    )
}
