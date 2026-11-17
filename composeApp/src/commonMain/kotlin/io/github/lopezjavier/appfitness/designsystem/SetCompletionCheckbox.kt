package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.vectorResource

/** Colours of [SetCompletionCheckbox]. */
@Immutable
data class SetCheckboxColors(val container: Color, val icon: Color)

object SetCompletionCheckboxDefaults {
    /** 05 §6 mandates 38 dp (off-grid by spec). */
    val VisualSize: Dp = 38.dp
    val TouchTarget: Dp = 48.dp
    val IconSize: Dp = 24.dp

    /** Ring of the next set to do (C-67). */
    val CurrentRingWidth: Dp = 1.5.dp
    val Shape = AppShapes.full

    /** Light around a done (or the current) circle. */
    val GlowRadius: Dp = 8.dp
    const val GLOW_ALPHA = 0.34f

    /** Both states draw the glyph; fill and glyph colour differ in luminance, so it survives greyscale. */
    fun colors(checked: Boolean, appColors: AppColors): SetCheckboxColors = if (checked) {
        SetCheckboxColors(container = appColors.neonLime, icon = appColors.onNeonLime)
    } else {
        SetCheckboxColors(container = appColors.surfaceControl, icon = appColors.textTertiary)
    }

    /** Completing a set is a Confirm (R-29); un-checking vibrates nothing. */
    fun hapticFor(newChecked: Boolean): HapticEvent? = if (newChecked) HapticEvent.Confirm else null
}

/** The lime glow of a checked circle, or the dimmer cyan one of the set to do next. */
private fun Modifier.setGlow(checked: Boolean, ringed: Boolean, colors: AppColors): Modifier = when {
    checked -> softGlow(
        colors.neonLime,
        SetCompletionCheckboxDefaults.VisualSize / 2,
        radius = SetCompletionCheckboxDefaults.GlowRadius,
        alpha = SetCompletionCheckboxDefaults.GLOW_ALPHA,
        offsetY = 0.dp,
    )

    ringed -> softGlow(
        colors.athleticCyan,
        SetCompletionCheckboxDefaults.VisualSize / 2,
        radius = SetCompletionCheckboxDefaults.GlowRadius,
        alpha = SetCompletionCheckboxDefaults.GLOW_ALPHA / 2,
        offsetY = 0.dp,
    )

    else -> this
}

/**
 * Round set-completion checkbox: 38 dp circle inside a 48 dp touch target. Haptics fire only from user
 * toggles. The caller's [modifier] lands on the toggleable node (exposed as checkable/checked).
 */
@Composable
fun SetCompletionCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    currentStateDescription: String? = null,
) {
    val haptics = LocalAppHaptics.current
    val colors = SetCompletionCheckboxDefaults.colors(checked, AppTheme.colors)
    // The next set to do carries a cyan ring and is spoken as such (C-67); it is never drawn on a checked circle.
    val ringed = currentStateDescription != null && !checked
    Box(
        modifier =
        modifier
            .size(SetCompletionCheckboxDefaults.TouchTarget)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = {
                    SetCompletionCheckboxDefaults.hapticFor(it)?.let(haptics::perform)
                    onCheckedChange(it)
                },
            )
            .semantics {
                this.contentDescription = contentDescription
                if (ringed) stateDescription = currentStateDescription.orEmpty()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .size(SetCompletionCheckboxDefaults.VisualSize)
                .setGlow(checked, ringed, AppTheme.colors)
                .clip(SetCompletionCheckboxDefaults.Shape)
                .background(colors.container)
                .then(if (checked) Modifier.background(AppPaint.diagonal(colors.container)) else Modifier)
                .then(
                    if (ringed) {
                        Modifier.border(
                            SetCompletionCheckboxDefaults.CurrentRingWidth,
                            AppTheme.colors.athleticCyan,
                            SetCompletionCheckboxDefaults.Shape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                imageVector = vectorResource(Res.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(SetCompletionCheckboxDefaults.IconSize),
                colorFilter = ColorFilter.tint(colors.icon),
            )
        }
    }
}

@Preview
@Composable
private fun SetCompletionCheckboxPreview() {
    AppTheme {
        Row(Modifier.padding(Spacing.lg)) {
            SetCompletionCheckbox(checked = false, onCheckedChange = {}, contentDescription = "Completar serie 1")
            SetCompletionCheckbox(checked = true, onCheckedChange = {}, contentDescription = "Completar serie 2")
        }
    }
}
