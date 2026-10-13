package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val Shape = AppShapes.full

    /** Both states draw the glyph; fill and glyph colour differ in luminance, so it survives greyscale. */
    fun colors(checked: Boolean, appColors: AppColors): SetCheckboxColors = if (checked) {
        SetCheckboxColors(container = appColors.neonLime, icon = appColors.onNeonLime)
    } else {
        SetCheckboxColors(container = appColors.surfaceOverlay, icon = appColors.ghostData)
    }

    fun hapticFor(newChecked: Boolean): HapticFeedbackType =
        if (newChecked) HapticFeedbackType.Confirm else HapticFeedbackType.ToggleOff
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
) {
    val haptic = LocalHapticFeedback.current
    val colors = SetCompletionCheckboxDefaults.colors(checked, AppTheme.colors)
    Box(
        modifier =
        modifier
            .size(SetCompletionCheckboxDefaults.TouchTarget)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = {
                    haptic.performHapticFeedback(SetCompletionCheckboxDefaults.hapticFor(it))
                    onCheckedChange(it)
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .size(SetCompletionCheckboxDefaults.VisualSize)
                .clip(SetCompletionCheckboxDefaults.Shape)
                .background(colors.container),
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
