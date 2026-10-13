package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Colours of the [AppBottomSheet]. */
@Immutable
data class AppBottomSheetColors(
    val container: Color,
    val content: Color,
    val scrim: Color,
    val handle: Color,
    val border: Color,
)

/** Sizes, shape and colour mapping of the [AppBottomSheet]. */
object AppBottomSheetDefaults {
    val HandleWidth: Dp = 36.dp
    val HandleHeight: Dp = 4.dp
    val Shape: Shape = AppShapes.sheetTop

    fun colors(appColors: AppColors): AppBottomSheetColors = AppBottomSheetColors(
        container = appColors.surfaceOverlay,
        content = appColors.textPrimary,
        scrim = appColors.scrim,
        handle = appColors.sheetHandle,
        border = appColors.borderStrong,
    )
}

/**
 * Modal bottom sheet (top radius 24, 36x4 handle, 65 % scrim). A scrim tap, back and a downward drag
 * all call [onDismissRequest]. To close it from a button, hide it first so it animates out:
 * `scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }`.
 *
 * The root is tagged [testTagPrefix] and the decorative handle `<prefix>_handle`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    testTagPrefix: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppBottomSheetDefaults.colors(AppTheme.colors)
    val shape = AppBottomSheetDefaults.Shape
    val shadow =
        Shadow(
            radius = AppElevation.sheetShadowBlur,
            color = AppTheme.colors.shadowSheet,
            spread = AppElevation.sheetShadowSpread,
            offset = DpOffset(0.dp, AppElevation.sheetShadowOffsetY),
        )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier =
        modifier
            // The sheet lives in its own window, so the main window's resource-id mapping does not reach it.
            .semantics { testTagsAsResourceId = true }
            .then(if (testTagPrefix != null) Modifier.testTag(testTagPrefix) else Modifier)
            .dropShadow(shape, shadow)
            .border(AppBorder.thin, colors.border, shape),
        sheetState = sheetState,
        shape = shape,
        containerColor = colors.container,
        contentColor = colors.content,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = { SheetHandle(colors.handle, testTagPrefix?.let { "${it}_handle" }) },
        content = content,
    )
}

@Composable
private fun SheetHandle(color: Color, testTag: String?) {
    // Decorative: cleared semantics, so the tag lives on a wrapper (same pattern as SkeletonBox).
    Box(Modifier.padding(vertical = Spacing.md).then(if (testTag != null) Modifier.testTag(testTag) else Modifier)) {
        Box(
            Modifier
                .clearAndSetSemantics {}
                .width(AppBottomSheetDefaults.HandleWidth)
                .height(AppBottomSheetDefaults.HandleHeight)
                .background(color, AppShapes.full),
        )
    }
}

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
                .border(AppBorder.thin, colors.border, AppBottomSheetDefaults.Shape),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetHandle(colors.handle, null)
            Text(
                "Título de ejemplo",
                color = colors.content,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            )
        }
    }
}
