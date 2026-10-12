package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp

/**
 * Shadow geometry tokens (planning/05-design-system.md §4). Depth normally comes from surface
 * layers; shadows are only for bottom sheets (T-027) and the active element glow (T-025).
 * Colors: [AppColors.shadowSheet] and [AppColors.glowLime].
 */
object AppElevation {
    val sheetShadowOffsetY = 12.dp
    val sheetShadowBlur = 32.dp
    val sheetShadowSpread = (-4).dp
    val glowBlur = 16.dp
}
