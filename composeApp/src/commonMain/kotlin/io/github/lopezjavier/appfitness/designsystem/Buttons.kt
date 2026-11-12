package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Visual weight of an [AppButton]. */
enum class AppButtonVariant { Primary, Secondary, Ghost }

/**
 * Colour treatment of a [GhostButton]. [Action] and [Neutral] are identical since round 0 (C-08: neutral label and
 * border, no cyan); the duplicate stays until the GhostButton audit (T-295). [Destructive] uses the error colour.
 */
enum class GhostTone { Action, Neutral, Destructive }

/** Colours of a button state. A null [border] draws none. */
@Immutable
data class AppButtonColors(val container: Color, val content: Color, val border: Color?)

/** Sizes, press feedback and colour mapping of the buttons. */
object AppButtonDefaults {
    val PrimaryMinHeight: Dp = 56.dp
    val MinHeight: Dp = 48.dp
    val Shape = AppShapes.lg
    val HorizontalPadding: Dp = Spacing.xl

    /** Minimum width of a short secondary action placed beside a full-width primary (e.g. "Atrás"). */
    val CompactMinWidth: Dp = 104.dp

    /** In-button progress indicator of a loading [PrimaryButton]. */
    val LoadingIndicatorSize: Dp = 20.dp
    val LoadingIndicatorStroke: Dp = 2.dp

    @Suppress("ktlint:standard:property-naming") // PascalCase kept to match the other defaults.
    const val PressedScale = 0.97f

    /** The secondary matches the primary so the two line up side by side. */
    fun minHeight(variant: AppButtonVariant): Dp =
        if (variant == AppButtonVariant.Ghost) MinHeight else PrimaryMinHeight

    /** Size of the optional leading icon of a button. */
    val LeadingIconSize: Dp = 20.dp

    /**
     * The meaning is carried by fill and text colour, never by the border alone. The disabled primary is
     * precomposed (lime 20 % over the base, label lime 50 % over that) so its contrast holds on any parent.
     */
    fun colors(
        variant: AppButtonVariant,
        enabled: Boolean,
        appColors: AppColors,
        ghostTone: GhostTone = GhostTone.Action,
    ): AppButtonColors = when (variant) {
        AppButtonVariant.Primary ->
            if (enabled) {
                AppButtonColors(appColors.neonLime, appColors.onNeonLime, border = null)
            } else {
                val container = appColors.neonLime.copy(
                    alpha = DISABLED_CONTAINER_ALPHA,
                ).compositeOver(appColors.surfaceBase)
                val content = appColors.neonLime.copy(alpha = DISABLED_CONTENT_ALPHA).compositeOver(container)
                AppButtonColors(container, content, border = null)
            }

        AppButtonVariant.Secondary ->
            AppButtonColors(
                container = appColors.surfaceControl,
                content = if (enabled) appColors.textPrimary else appColors.ghostData,
                border = null,
            )

        AppButtonVariant.Ghost -> {
            val accent =
                when (ghostTone) {
                    GhostTone.Action, GhostTone.Neutral -> appColors.textPrimary
                    GhostTone.Destructive -> appColors.error
                }
            AppButtonColors(
                container = Color.Transparent,
                content = if (enabled) accent else appColors.ghostData,
                border =
                if (enabled) {
                    if (ghostTone == GhostTone.Destructive) accent else appColors.borderControl
                } else {
                    appColors.borderSubtle
                },
            )
        }
    }

    /** Border drawn by a button: the ghost shows a 2 dp lime focus border while focused and enabled. */
    fun borderStroke(
        variant: AppButtonVariant,
        enabled: Boolean,
        focused: Boolean,
        colors: AppButtonColors,
        appColors: AppColors,
    ): BorderStroke? = if (variant == AppButtonVariant.Ghost && focused && enabled) {
        BorderStroke(AppBorder.focus, appColors.neonLime)
    } else {
        colors.border?.let { BorderStroke(AppBorder.thin, it) }
    }

    private const val DISABLED_CONTAINER_ALPHA = 0.2f
    private const val DISABLED_CONTENT_ALPHA = 0.5f

    fun scale(pressed: Boolean): Float = if (pressed) PressedScale else 1f
}

/**
 * Lime-filled main action. While [loading] a small progress indicator sits at the end of the button (the text
 * does not change); pass `enabled = false` as well to block taps.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    loading: Boolean = false,
) = AppButton(AppButtonVariant.Primary, text, onClick, modifier, enabled, interactionSource, loading = loading)

/** Filled secondary action (raised surface, no border), as tall as [PrimaryButton] so the two can sit side by side. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    leadingIcon: DrawableResource? = null,
) = AppButton(
    AppButtonVariant.Secondary,
    text,
    onClick,
    modifier,
    enabled,
    interactionSource,
    leadingIcon = leadingIcon,
)

/**
 * Transparent action with a label and a neutral border; [tone] picks the destructive treatment.
 * [loading] shows the same end-of-button progress indicator as [PrimaryButton].
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    tone: GhostTone = GhostTone.Action,
    loading: Boolean = false,
) = AppButton(AppButtonVariant.Ghost, text, onClick, modifier, enabled, interactionSource, tone, loading)

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
    ghostTone: GhostTone = GhostTone.Action,
    loading: Boolean = false,
    leadingIcon: DrawableResource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val scale by animateFloatAsState(AppButtonDefaults.scale(pressed), label = "button_press_scale")
    val colors = AppButtonDefaults.colors(variant, enabled, AppTheme.colors, ghostTone)
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
        border =
        AppButtonDefaults.borderStroke(variant, enabled, focused, colors, AppTheme.colors),
        interactionSource = source,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = AppButtonDefaults.HorizontalPadding, vertical = Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    // Decorative: the label carries the meaning.
                    Icon(
                        painter = painterResource(leadingIcon),
                        contentDescription = null,
                        modifier = Modifier.size(AppButtonDefaults.LeadingIconSize).testTag("app_button_leading_icon"),
                        tint = colors.content,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(text = text, style = AppTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            if (loading) {
                // Decorative: the caller exposes the busy state through the button's semantics.
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterEnd).size(AppButtonDefaults.LoadingIndicatorSize),
                    color = AppTheme.colors.neonLime,
                    strokeWidth = AppButtonDefaults.LoadingIndicatorStroke,
                )
            }
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
