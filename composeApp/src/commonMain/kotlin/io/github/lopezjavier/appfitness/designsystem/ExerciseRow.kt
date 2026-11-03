package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_row_open_detail_cd
import io.github.lopezjavier.appfitness.composeapp.resources.favourite_state_on
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Exercise row (R-16, R-18): the thumbnail ([imageUri], the placeholder when null) and the [name] with an optional
 * [supporting] line, on an [AppListRow]. By default tapping the thumbnail or the name calls [onOpen] (the exercise
 * details). With [onSelect] (a picker that chooses an exercise, R-17) the row body calls [onSelect] and the thumbnail
 * becomes its own target (at least 48 dp, "Ver detalle de <name>", tag `<tagPrefix>_thumbnail`) that calls [onOpen].
 * [supportingTestTag] tags the supporting line. [trailing] is a separate slot for the caller's own controls.
 * [favorite] draws the filled star marker after the name (R-56, T-204; tagged [favoriteTestTag]) and adds "Favorito"
 * to the row description. The row is tagged [tagPrefix]; the thumbnail
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
    onSelect: (() -> Unit)? = null,
    supportingTestTag: String? = null,
    favorite: Boolean = false,
    favoriteTestTag: String? = null,
) {
    val thumbnail: @Composable () -> Unit = {
        ExerciseThumbnail(
            imageUri = imageUri,
            size = ExerciseThumbnailDefaults.RowSize,
            testTagPrefix = tagPrefix,
        )
    }
    val detailDescription = stringResource(Res.string.exercise_row_open_detail_cd, name)
    AppListRow(
        headline = name,
        modifier = modifier.testTag(tagPrefix),
        supporting = supporting,
        leading = if (onSelect == null) {
            thumbnail
        } else {
            {
                Box(
                    Modifier
                        .defaultMinSize(Spacing.minTouchTarget, Spacing.minTouchTarget)
                        .clickable(role = Role.Button, onClick = onOpen)
                        .semantics { contentDescription = detailDescription }
                        .testTag("${tagPrefix}_thumbnail"),
                    contentAlignment = Alignment.Center,
                ) { thumbnail() }
            }
        },
        trailing = if (favorite) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavoriteMarker(favoriteTestTag)
                    trailing?.invoke()
                }
            }
        } else {
            trailing
        },
        supportingTestTag = supportingTestTag,
        onClick = onSelect ?: onOpen,
    )
}

/** The non-interactive filled star of a favourite row; its text "Favorito" is read with the row. */
@Composable
private fun FavoriteMarker(testTag: String?) {
    val description = stringResource(Res.string.favourite_state_on)
    Icon(
        painter = painterResource(FavouriteToggleDefaults.icon(true)),
        contentDescription = description,
        tint = FavouriteToggleDefaults.tint(true, true, AppTheme.colors),
        modifier = Modifier.size(FAVORITE_MARKER_SIZE).let { if (testTag != null) it.testTag(testTag) else it },
    )
}

private val FAVORITE_MARKER_SIZE = 16.dp
