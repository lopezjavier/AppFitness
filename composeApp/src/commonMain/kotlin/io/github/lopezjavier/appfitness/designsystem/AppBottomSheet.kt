package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours of the [AppBottomSheet]. */
@Immutable
data class AppBottomSheetColors(val container: Color, val content: Color, val scrim: Color, val border: Color)

/** Sizes, shape and colour mapping of the [AppBottomSheet]. */
object AppBottomSheetDefaults {
    val Shape: Shape = AppShapes.sheetTop

    /** The hairline covers the top edge and the two corner arcs only: it is clipped below the top radius. */
    val HairlineClipHeight: Dp = AppRadius.xl

    fun colors(appColors: AppColors): AppBottomSheetColors = AppBottomSheetColors(
        container = appColors.surfaceOverlay,
        content = appColors.textPrimary,
        scrim = appColors.scrim,
        border = appColors.borderStrong,
    )

    /**
     * A [AppBorder.thin] outline of [shape] along the top edge and both corner arcs, nothing along the sides or
     * the bottom (SHT-3). The outline is centred on the edge and the sheet clips to its shape, so the visible
     * stroke is [width] wide. Apply it to the first child inside the sheet, not to the sheet's own modifier,
     * which lives outside the sheet's drag offset and would draw away from the sheet.
     */
    fun Modifier.topHairline(
        color: Color,
        shape: Shape = Shape,
        width: Dp = AppBorder.thin,
        clipHeight: Dp = HairlineClipHeight,
    ): Modifier = drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        clipRect(bottom = clipHeight.toPx()) {
            drawOutline(outline, color, style = Stroke(width = width.toPx() * 2))
        }
    }
}

/**
 * Modal bottom sheet (top radius 24, no drag handle, 65 % scrim, top-only hairline, R-38, ADR-80). Back calls
 * [onDismissRequest]; so does a tap outside the sheet, but only when [dismissOnClickOutside] is true (pickers, info
 * sheets and action menus; never a sheet with editable input). A drag never closes it. To close it from a button,
 * hide it first so it animates out: `scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }`.
 *
 * The root is tagged [testTagPrefix]. Screens use the archetypes
 * ([AppEditSheet], [AppPickerSheet], [AppInfoSheet], [AppActionSheet]) rather than this surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberAppSheetState(),
    testTagPrefix: String? = null,
    dismissOnClickOutside: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppBottomSheetDefaults.colors(AppTheme.colors)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier =
        modifier
            // The sheet lives in its own window, so the main window's resource-id mapping does not reach it.
            .semantics { testTagsAsResourceId = true }
            .then(if (testTagPrefix != null) Modifier.testTag(testTagPrefix) else Modifier),
        sheetState = sheetState,
        shape = AppBottomSheetDefaults.Shape,
        containerColor = colors.container,
        contentColor = colors.content,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = null,
        sheetGesturesEnabled = false,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = true,
            shouldDismissOnClickOutside = dismissOnClickOutside,
        ),
    ) {
        with(AppBottomSheetDefaults) {
            Column(Modifier.fillMaxWidth().topHairline(colors.border)) {
                content()
            }
        }
    }
}

/**
 * The one sheet state of the app (R-38): the sheet cannot be swiped away (gestures are off in [AppBottomSheet]),
 * but `hide()` from X, Cancelar or Guardar still animates it out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppSheetState(): SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

@Preview
@Composable
private fun AppBottomSheetPreview() {
    AppTheme {
        // The real sheet opens in its own window, so the preview shows its static surface.
        val colors = AppBottomSheetDefaults.colors(AppTheme.colors)
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.container, AppBottomSheetDefaults.Shape)
                .clip(AppBottomSheetDefaults.Shape)
                .then(with(AppBottomSheetDefaults) { Modifier.topHairline(colors.border) }),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Título de ejemplo",
                color = colors.content,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            )
        }
    }
}
