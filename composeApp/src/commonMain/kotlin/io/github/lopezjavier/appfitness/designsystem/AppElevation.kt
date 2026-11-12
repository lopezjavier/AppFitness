package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.unit.dp

/**
 * Shadow geometry tokens (planning/05-design-system.md §4). Depth normally comes from surface
 * layers; shadows are only for bottom sheets (T-027). Color: [AppColors.shadowSheet].
 */
object AppElevation {
    val sheetShadowOffsetY = 12.dp
    val sheetShadowBlur = 32.dp
    val sheetShadowSpread = (-4).dp
}
