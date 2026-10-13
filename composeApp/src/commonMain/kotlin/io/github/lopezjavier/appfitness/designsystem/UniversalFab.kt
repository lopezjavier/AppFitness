package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours of the [UniversalFab]. */
@Immutable
data class UniversalFabColors(val container: Color, val content: Color)

/** Sizes, press feedback and colours of the [UniversalFab]. */
object UniversalFabDefaults {
    val Height: Dp = 52.dp

    /** Distance to the screen edges (M3 Scaffold FAB margin). */
    val EdgeMargin: Dp = Spacing.lg

    /** Bottom space scrollable content leaves so its last item is not hidden under the FAB. */
    val ContentClearance: Dp = Height + EdgeMargin * 2

    /** Shares the button press scale, so there is a single source. */
    fun scale(pressed: Boolean): Float = if (pressed) AppButtonDefaults.PressedScale else 1f

    fun colors(appColors: AppColors): UniversalFabColors = UniversalFabColors(appColors.neonLime, appColors.onNeonLime)
}

/**
 * Lime pill action button, usually labelled "+ Registro". It does no positioning: the shell places it
 * (Scaffold FAB slot on phones, rail header on tablets).
 */
@Composable
fun UniversalFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(UniversalFabDefaults.scale(pressed), label = "fab_press_scale")
    val colors = UniversalFabDefaults.colors(AppTheme.colors)
    Surface(
        onClick = onClick,
        modifier =
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { role = Role.Button }
            .heightIn(min = UniversalFabDefaults.Height),
        shape = AppShapes.full,
        color = colors.container,
        contentColor = colors.content,
        interactionSource = source,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = AppTheme.typography.titleMedium)
        }
    }
}

@Preview
@Composable
private fun UniversalFabPreview() {
    AppTheme { UniversalFab("+ Registro", onClick = {}) }
}
