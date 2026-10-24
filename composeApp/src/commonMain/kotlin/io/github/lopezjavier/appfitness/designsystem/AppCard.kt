package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/** Colours of [AppCard]. */
@Immutable
data class AppCardColors(val container: Color, val border: Color)

/** Shape, spacing and colours of [AppCard] (planning/05-design-system.md §6). */
object AppCardDefaults {
    val Shape = AppShapes.lg
    val Padding: Dp = Spacing.cardPadding
    val BorderWidth: Dp = AppBorder.thin

    /** Border of a [highlighted] card (training domain: cyan, 1.5 dp). */
    val HighlightBorderWidth: Dp = AppBorder.focus

    fun colors(appColors: AppColors, highlighted: Boolean = false): AppCardColors = AppCardColors(
        container = appColors.surfaceRaised,
        border = if (highlighted) appColors.athleticCyan else appColors.borderSubtle,
    )
}

/**
 * Tier-1 card: `surfaceRaised` with a 1 dp border at 8% white and a 16 dp radius. It draws no shadow;
 * depth comes from the tonal step and the border. [highlighted] swaps the border for a 1.5 dp cyan one (callers
 * add a non-colour cue too).
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppCardDefaults.colors(AppTheme.colors, highlighted)
    val borderWidth = if (highlighted) AppCardDefaults.HighlightBorderWidth else AppCardDefaults.BorderWidth
    Column(
        modifier =
        modifier
            .clip(AppCardDefaults.Shape)
            .background(colors.container)
            .border(borderWidth, colors.border, AppCardDefaults.Shape)
            .padding(AppCardDefaults.Padding),
        content = content,
    )
}

@Preview
@Composable
private fun AppCardPreview() {
    AppTheme {
        AppCard(Modifier.fillMaxWidth()) {
            Text("Tarjeta", style = AppTheme.typography.titleMedium, color = AppTheme.colors.textPrimary)
            Text("Contenido", style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
