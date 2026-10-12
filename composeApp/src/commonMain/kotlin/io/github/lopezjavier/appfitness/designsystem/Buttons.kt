package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visual weight of an [AppButton]. */
enum class AppButtonVariant { Primary, Secondary, Ghost }

/** Colours of a button state. A null [border] draws none. */
@Immutable
data class AppButtonColors(val container: Color, val content: Color, val border: Color?)

/** Sizes, press feedback and colour mapping of the buttons. */
object AppButtonDefaults {
    val PrimaryMinHeight: Dp = 52.dp
    val MinHeight: Dp = 48.dp
    val Shape = AppShapes.md
    val HorizontalPadding: Dp = Spacing.xl

    @Suppress("ktlint:standard:property-naming") // PascalCase kept to match the other defaults.
    const val PressedScale = 0.97f

    fun minHeight(variant: AppButtonVariant): Dp =
        if (variant == AppButtonVariant.Primary) PrimaryMinHeight else MinHeight

    /**
     * The meaning is carried by fill and text colour, never by the border alone. Cyan on the ghost
     * button is its action treatment, not a training-domain signal: do not reuse it for other meanings.
     */
    fun colors(variant: AppButtonVariant, enabled: Boolean, appColors: AppColors): AppButtonColors = when (variant) {
        AppButtonVariant.Primary ->
            if (enabled) {
                AppButtonColors(appColors.neonLime, appColors.onNeonLime, border = null)
            } else {
                AppButtonColors(appColors.surfaceOverlay, appColors.ghostData, border = null)
            }

        AppButtonVariant.Secondary ->
            AppButtonColors(
                container = appColors.surfaceOverlay,
                content = if (enabled) appColors.textPrimary else appColors.ghostData,
                border = if (enabled) appColors.borderStrong else appColors.borderSubtle,
            )

        AppButtonVariant.Ghost ->
            AppButtonColors(
                container = Color.Transparent,
                content = if (enabled) appColors.athleticCyan else appColors.ghostData,
                border = if (enabled) appColors.athleticCyan else appColors.borderSubtle,
            )
    }

    fun scale(pressed: Boolean): Float = if (pressed) PressedScale else 1f
}

/** Lime-filled main action. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = AppButton(AppButtonVariant.Primary, text, onClick, modifier, enabled, interactionSource)

/** Raised secondary action with a strong border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = AppButton(AppButtonVariant.Secondary, text, onClick, modifier, enabled, interactionSource)

/** Transparent action with a cyan label and border. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) = AppButton(AppButtonVariant.Ghost, text, onClick, modifier, enabled, interactionSource)

// Private shared implementation: the public wrappers own the defaults.
@Suppress("LongParameterList", "ModifierWithoutDefault")
@Composable
private fun AppButton(
    variant: AppButtonVariant,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(AppButtonDefaults.scale(pressed), label = "button_press_scale")
    val colors = AppButtonDefaults.colors(variant, enabled, AppTheme.colors)
    Surface(
        onClick = onClick,
        modifier =
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = AppButtonDefaults.minHeight(variant)),
        enabled = enabled,
        shape = AppButtonDefaults.Shape,
        color = colors.container,
        contentColor = colors.content,
        border = colors.border?.let { BorderStroke(AppBorder.thin, it) },
        interactionSource = source,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = AppButtonDefaults.HorizontalPadding, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, style = AppTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    AppTheme {
        Column(Modifier.padding(Spacing.screenMargin), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton("Primario", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Deshabilitado", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            SecondaryButton("Secundario", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Deshabilitado", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            GhostButton("Fantasma", onClick = {}, modifier = Modifier.fillMaxWidth())
            GhostButton("Deshabilitado", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
    }
}
