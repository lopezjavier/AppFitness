@file:Suppress("MatchingDeclarationName") // The defaults object sits beside the components, like the others.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

object TonalIconControlsDefaults {
    /** Diameter of the circle of a [CircleIconButton] (the 48 dp target around it stays). */
    val CircleSize: Dp = 40.dp

    /** Glyph of every control in this file. */
    val GlyphSize: Dp = 24.dp

    /** Side of a [TonalSquareIconButton]. */
    val SquareSize: Dp = 56.dp

    /** Side of an [IconTile]. */
    val TileSize: Dp = 40.dp
    private const val DISABLED_CONTAINER_ALPHA = 0.2f
    private const val DISABLED_CONTENT_ALPHA = 0.5f

    /** Fill and glyph of a [CircleIconButton]; the accent disabled fill is precomposed like the primary button's. */
    fun colors(accent: Boolean, enabled: Boolean, appColors: AppColors, disabledGlyph: Color?): Pair<Color, Color> =
        when {
            accent && enabled -> appColors.neonLime to appColors.onNeonLime

            accent -> {
                val container = appColors.neonLime.copy(alpha = DISABLED_CONTAINER_ALPHA)
                    .compositeOver(appColors.surfaceBase)
                container to appColors.neonLime.copy(alpha = DISABLED_CONTENT_ALPHA).compositeOver(container)
            }

            enabled -> appColors.surfaceControl to appColors.textPrimary

            else -> appColors.surfaceControl to (disabledGlyph ?: appColors.ghostData)
        }
}

/**
 * Round icon-only button: a [size] circle (40 dp by default) in a 48 dp touch target. [accent] draws it lime (the
 * next routine's start); otherwise it is tonal (`surfaceControl`). A disabled tonal button dims its glyph to
 * `ghostData`, or to [disabledGlyph] when the caller wants it to say something (a run in progress). The description
 * and [testTag] sit on the button; the glyph is decorative.
 */
@Composable
fun CircleIconButton(
    contentDescription: String,
    icon: DrawableResource,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    size: Dp = TonalIconControlsDefaults.CircleSize,
    disabledGlyph: Color? = null,
) {
    val (container, glyph) = TonalIconControlsDefaults.colors(accent, enabled, AppTheme.colors, disabledGlyph)
    Box(
        modifier
            .size(SetCompletionCheckboxDefaults.TouchTarget)
            .clip(AppShapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clip(AppShapes.full).background(container), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = glyph,
                modifier = Modifier.size(TonalIconControlsDefaults.GlyphSize),
            )
        }
    }
}

/**
 * Square icon-only button, 56 dp, `surfaceControl`, radius 16 dp: the companion of a full-width add button. A long
 * press shows [contentDescription] as a tooltip; disabled it is inert and its glyph is `ghostData`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TonalSquareIconButton(
    contentDescription: String,
    icon: DrawableResource,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val button: @Composable () -> Unit = {
        Box(
            modifier
                .size(TonalIconControlsDefaults.SquareSize)
                .clip(AppShapes.lg)
                .background(AppTheme.colors.surfaceControl)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.ghostData,
                modifier = Modifier.size(TonalIconControlsDefaults.GlyphSize),
            )
        }
    }
    if (!enabled) {
        button()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(modifier = Modifier.clearAndSetSemantics { this.testTag = "${testTag}_tooltip" }) {
                Text(contentDescription)
            }
        },
        state = rememberTooltipState(),
        content = button,
    )
}

/** A 40 dp rounded tile (`surfaceControl`, radius 12 dp) holding a decorative 24 dp glyph in `textSecondary`. */
@Composable
fun IconTile(icon: DrawableResource, modifier: Modifier = Modifier) {
    Box(
        modifier.size(TonalIconControlsDefaults.TileSize).clip(AppShapes.md).background(AppTheme.colors.surfaceControl),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(TonalIconControlsDefaults.GlyphSize),
        )
    }
}
