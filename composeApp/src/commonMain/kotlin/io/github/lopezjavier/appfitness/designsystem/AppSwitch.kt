@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry and colour mapping of [AppSwitch]. */
object AppSwitchDefaults {
    val TouchTarget: Dp = Spacing.minTouchTarget
    val TrackWidth: Dp = 52.dp
    val TrackHeight: Dp = 32.dp
    val ThumbSize: Dp = 24.dp
    val ThumbPadding: Dp = Spacing.xs

    /** Alpha of a disabled switch (same rule as the other controls). */
    const val DISABLED_ALPHA = 0.38f

    /** Lime track when on, raised surface when off: the thumb position also tells the state, not colour alone. */
    fun trackColor(checked: Boolean, appColors: AppColors): Color =
        if (checked) appColors.neonLime else appColors.surfaceOverlay

    fun thumbColor(checked: Boolean, appColors: AppColors): Color =
        if (checked) appColors.onNeonLime else appColors.textSecondary

    fun borderColor(checked: Boolean, appColors: AppColors): Color =
        if (checked) appColors.neonLime else appColors.borderStrong
}

/**
 * On/off switch with a 48 dp touch target. It exposes `Role.Switch` and the toggle state; [stateDescription] should
 * say the state in words ("Activada" / "Desactivada"), [contentDescription] what it controls. The caller's
 * [modifier] (tag included) lands on the toggleable node. A disabled switch is inert and dimmed.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stateDescription: String? = null,
) {
    val colors = AppTheme.colors
    Box(
        modifier
            .size(AppSwitchDefaults.TrackWidth, AppSwitchDefaults.TouchTarget)
            .alpha(if (enabled) 1f else AppSwitchDefaults.DISABLED_ALPHA)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics {
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(AppSwitchDefaults.TrackWidth, AppSwitchDefaults.TrackHeight)
                .clip(AppShapes.full)
                .background(AppSwitchDefaults.trackColor(checked, colors))
                .border(AppBorder.thin, AppSwitchDefaults.borderColor(checked, colors), AppShapes.full)
                .padding(AppSwitchDefaults.ThumbPadding),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .size(AppSwitchDefaults.ThumbSize)
                    .clip(AppShapes.full)
                    .background(AppSwitchDefaults.thumbColor(checked, colors)),
            )
        }
    }
}

@Preview
@Composable
private fun AppSwitchPreview() {
    AppTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            AppSwitch(checked = true, onCheckedChange = {}, contentDescription = "Activada")
            AppSwitch(checked = false, onCheckedChange = {}, contentDescription = "Desactivada")
            AppSwitch(checked = true, onCheckedChange = {}, contentDescription = "Bloqueada", enabled = false)
        }
    }
}
