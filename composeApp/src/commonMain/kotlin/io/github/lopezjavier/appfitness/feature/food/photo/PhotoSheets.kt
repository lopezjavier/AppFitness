package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_capture_error_camera
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_capture_error_gallery
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_barcode_hint
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_barcode_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_estimate_hint
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_estimate_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_label_hint
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_label_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_mode_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_source_camera_hint
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_source_camera_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_source_gallery_hint
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_source_gallery_title
import io.github.lopezjavier.appfitness.composeapp.resources.food_photo_source_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_auto_awesome
import io.github.lopezjavier.appfitness.composeapp.resources.ic_barcode_scanner
import io.github.lopezjavier.appfitness.composeapp.resources.ic_label
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_camera
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_library
import io.github.lopezjavier.appfitness.designsystem.AppActionSheet
import io.github.lopezjavier.appfitness.designsystem.AppSheetAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import org.jetbrains.compose.resources.stringResource

/** First sheet of the "add from photo" flow: what kind of photo. Scrim, swipe and back close the flow. */
@Composable
fun PhotoModeSheet(onModeChoose: (FoodPhotoMode) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // A choice moves the flow on: the sheet's own closing callback must not cancel it.
    var chosen by remember { mutableStateOf(false) }
    val choose = { mode: FoodPhotoMode ->
        chosen = true
        onModeChoose(mode)
    }
    AppActionSheet(
        actions = listOf(
            AppSheetAction(
                icon = Res.drawable.ic_auto_awesome,
                title = stringResource(Res.string.food_photo_mode_estimate_title),
                hint = stringResource(Res.string.food_photo_mode_estimate_hint),
                onClick = { choose(FoodPhotoMode.Dish) },
            ),
            AppSheetAction(
                icon = Res.drawable.ic_label,
                title = stringResource(Res.string.food_photo_mode_label_title),
                hint = stringResource(Res.string.food_photo_mode_label_hint),
                onClick = { choose(FoodPhotoMode.Label) },
            ),
            AppSheetAction(
                icon = Res.drawable.ic_barcode_scanner,
                title = stringResource(Res.string.food_photo_mode_barcode_title),
                hint = stringResource(Res.string.food_photo_mode_barcode_hint),
                onClick = { choose(FoodPhotoMode.Barcode) },
            ),
        ),
        onDismiss = { if (!chosen) onDismiss() },
        modifier = modifier,
        title = stringResource(Res.string.food_photo_mode_title),
        testTagPrefix = "photo_mode",
    )
}

/**
 * Second sheet: camera or gallery. After a failed capture ([captureFailedFor] is the source that failed) a short
 * message appears inside the sheet; a cancelled capture shows nothing. Tags: `<tagPrefix>_row_1` (camera),
 * `_row_2` (gallery), `_close` and `<tagPrefix>_capture_error`.
 */
@Composable
fun PhotoSourceSheet(
    onSourceChoose: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    captureFailedFor: PhotoSource? = null,
    tagPrefix: String = "photo_source",
) {
    var chosen by remember { mutableStateOf(false) }
    val choose = { source: PhotoSource ->
        chosen = true
        onSourceChoose(source)
    }
    AppActionSheet(
        actions = listOf(
            AppSheetAction(
                icon = Res.drawable.ic_photo_camera,
                title = stringResource(Res.string.food_photo_source_camera_title),
                hint = stringResource(Res.string.food_photo_source_camera_hint),
                onClick = { choose(PhotoSource.Camera) },
            ),
            AppSheetAction(
                icon = Res.drawable.ic_photo_library,
                title = stringResource(Res.string.food_photo_source_gallery_title),
                hint = stringResource(Res.string.food_photo_source_gallery_hint),
                onClick = { choose(PhotoSource.Gallery) },
            ),
        ),
        onDismiss = { if (!chosen) onDismiss() },
        modifier = modifier,
        title = stringResource(Res.string.food_photo_source_title),
        testTagPrefix = tagPrefix,
        notice = captureFailedFor?.let { failed ->
            {
                Text(
                    text = stringResource(
                        if (failed == PhotoSource.Camera) {
                            Res.string.food_photo_capture_error_camera
                        } else {
                            Res.string.food_photo_capture_error_gallery
                        },
                    ),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.error,
                    modifier = Modifier
                        .padding(horizontal = Spacing.screenMargin)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("${tagPrefix}_capture_error"),
                )
            }
        },
    )
}
