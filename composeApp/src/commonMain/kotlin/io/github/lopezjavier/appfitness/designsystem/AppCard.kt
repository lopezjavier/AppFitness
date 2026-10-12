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

    fun colors(appColors: AppColors): AppCardColors =
        AppCardColors(container = appColors.surfaceRaised, border = appColors.borderSubtle)
}

/**
 * Tier-1 card: `surfaceRaised` with a 1 dp border at 8% white and a 16 dp radius. It draws no shadow;
 * depth comes from the tonal step and the border.
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppCardDefaults.colors(AppTheme.colors)
    Column(
        modifier =
        modifier
            .clip(AppCardDefaults.Shape)
            .background(colors.container)
            .border(AppCardDefaults.BorderWidth, colors.border, AppCardDefaults.Shape)
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
