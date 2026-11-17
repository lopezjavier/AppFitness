package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_row_open_detail_cd
import io.github.lopezjavier.appfitness.composeapp.resources.favourite_state_on
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Exercise row (R-16, R-18): the thumbnail ([imageUri], the placeholder when null) and the [name] with an optional
 * [supporting] line, on an [AppListRow]. By default tapping the thumbnail or the name calls [onOpen] (the exercise
 * details). With [onSelect] (a picker that chooses an exercise, R-17) the row body calls [onSelect] and the thumbnail
 * becomes its own target (at least 48 dp, "Ver detalle de <name>", tag `<tagPrefix>_thumbnail`) that calls [onOpen].
 * [supportingTestTag] tags the supporting line; [supportingMaxLines] caps it with an ellipsis (default unlimited).
 * With [enabled] false the row body does nothing (the caller says why in [supporting]);
 * the thumbnail still opens the details.
 * [supportingSecondary] is a second supporting line (`bodyMedium`, `textSecondary`; C-116, "puedes cambiarlo").
 * [trailing] is a separate slot for the caller's own controls.
 * [selected] makes the row a single-choice option (R-64 b): null is a plain row; non-null adds the `selected`
 * semantics and, when true and [trailing] is empty, a `neonLime` check (no fill, tag `<tagPrefix>_selected_check`).
 * [favorite] draws the filled star marker inline right after the name, through `AppListRow.headlineTrailing`, so
 * [trailing] stays free (R-56, T-204; tagged [favoriteTestTag]) and adds "Favorito"
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
    enabled: Boolean = true,
    supportingMaxLines: Int = Int.MAX_VALUE,
    supportingSecondary: String? = null,
    selected: Boolean? = null,
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
        modifier = modifier.testTag(tagPrefix).let { base ->
            if (selected == null) base else base.semantics { this.selected = selected }
        },
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
        trailing = trailing ?: if (selected == true) {
            { SelectedCheck(tagPrefix) }
        } else {
            null
        },
        headlineTrailing = if (favorite) {
            { FavoriteMarker(favoriteTestTag) }
        } else {
            null
        },
        supportingTestTag = supportingTestTag,
        supportingMaxLines = supportingMaxLines,
        supportingSecondary = supportingSecondary,
        onClick = onSelect ?: onOpen,
        enabled = enabled,
    )
}

/** The decorative `neonLime` check of a selected row (R-64 b); the row's `selected` semantics carry the state. */
@Composable
private fun SelectedCheck(tagPrefix: String) {
    Icon(
        painter = painterResource(Res.drawable.ic_check),
        contentDescription = null,
        tint = AppTheme.colors.neonLime,
        modifier = Modifier.size(Spacing.xl).testTag("${tagPrefix}_selected_check"),
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
