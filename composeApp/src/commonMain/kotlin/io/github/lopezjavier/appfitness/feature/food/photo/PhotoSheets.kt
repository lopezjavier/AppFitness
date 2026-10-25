@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.food.photo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
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
import io.github.lopezjavier.appfitness.designsystem.ActionRow
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.FoodPhotoMode
import io.github.lopezjavier.appfitness.domain.port.PhotoSource
import org.jetbrains.compose.resources.stringResource

/** First sheet of the "add from photo" flow: what kind of photo. Scrim, swipe and back close the flow. */
@Composable
fun PhotoModeSheet(onModeChoose: (FoodPhotoMode) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "food_photo_mode_sheet") {
        SheetColumn(stringResource(Res.string.food_photo_mode_title)) {
            ActionRow(
                icon = Res.drawable.ic_auto_awesome,
                title = stringResource(Res.string.food_photo_mode_estimate_title),
                hint = stringResource(Res.string.food_photo_mode_estimate_hint),
                onClick = { onModeChoose(FoodPhotoMode.Dish) },
                testTag = "food_photo_mode_estimate",
            )
            ActionRow(
                icon = Res.drawable.ic_label,
                title = stringResource(Res.string.food_photo_mode_label_title),
                hint = stringResource(Res.string.food_photo_mode_label_hint),
                onClick = { onModeChoose(FoodPhotoMode.Label) },
                testTag = "food_photo_mode_label",
            )
            ActionRow(
                icon = Res.drawable.ic_barcode_scanner,
                title = stringResource(Res.string.food_photo_mode_barcode_title),
                hint = stringResource(Res.string.food_photo_mode_barcode_hint),
                onClick = { onModeChoose(FoodPhotoMode.Barcode) },
                testTag = "food_photo_mode_barcode",
            )
        }
    }
}

/**
 * Second sheet: camera or gallery. After a failed capture ([captureFailedFor] is the source that failed) a short
 * message appears inside the sheet; a cancelled capture shows nothing.
 */
@Composable
fun PhotoSourceSheet(
    onSourceChoose: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    captureFailedFor: PhotoSource? = null,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "food_photo_source_sheet") {
        SheetColumn(stringResource(Res.string.food_photo_source_title)) {
            if (captureFailedFor != null) {
                Text(
                    text = stringResource(
                        if (captureFailedFor == PhotoSource.Camera) {
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
                        .testTag("food_photo_capture_error"),
                )
            }
            ActionRow(
                icon = Res.drawable.ic_photo_camera,
                title = stringResource(Res.string.food_photo_source_camera_title),
                hint = stringResource(Res.string.food_photo_source_camera_hint),
                onClick = { onSourceChoose(PhotoSource.Camera) },
                testTag = "food_photo_source_camera",
            )
            ActionRow(
                icon = Res.drawable.ic_photo_library,
                title = stringResource(Res.string.food_photo_source_gallery_title),
                hint = stringResource(Res.string.food_photo_source_gallery_hint),
                onClick = { onSourceChoose(PhotoSource.Gallery) },
                testTag = "food_photo_source_gallery",
            )
        }
    }
}

@Composable
private fun SheetColumn(title: String, content: @Composable () -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg)) {
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm).semantics {
                heading()
            },
        )
        content()
    }
}
