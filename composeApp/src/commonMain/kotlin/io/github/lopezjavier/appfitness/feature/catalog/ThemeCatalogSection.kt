package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_theme
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_theme_colors
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_theme_typography
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppColors
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTypography
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private val SwatchSize: Dp = Spacing.xl

/** Token names are code identifiers, not translatable copy. */
internal val ColorTokens: List<Pair<String, (AppColors) -> Color>> =
    listOf(
        "surfaceBase" to { it.surfaceBase },
        "surfaceRaised" to { it.surfaceRaised },
        "surfaceOverlay" to { it.surfaceOverlay },
        "borderSubtle" to { it.borderSubtle },
        "borderStrong" to { it.borderStrong },
        "divider" to { it.divider },
        "textPrimary" to { it.textPrimary },
        "textSecondary" to { it.textSecondary },
        "ghostData" to { it.ghostData },
        "scrim" to { it.scrim },
        "sheetHandle" to { it.sheetHandle },
        "neonLime" to { it.neonLime },
        "onNeonLime" to { it.onNeonLime },
        "athleticCyan" to { it.athleticCyan },
        "energyAmber" to { it.energyAmber },
        "electricGreen" to { it.electricGreen },
        "error" to { it.error },
        "onError" to { it.onError },
        "errorContainer" to { it.errorContainer },
        "onErrorContainer" to { it.onErrorContainer },
        "macroProtein" to { it.macroProtein },
        "macroCarbs" to { it.macroCarbs },
        "macroFat" to { it.macroFat },
        "shadowSheet" to { it.shadowSheet },
        "glowLime" to { it.glowLime },
    )

/** The nine styles in the order of design system section 3; the uppercase flag follows the label style rule. */
private val TypeStyles: List<Triple<String, (AppTypography) -> TextStyle, Boolean>> =
    listOf(
        Triple("displayHero", { t: AppTypography -> t.displayHero }, false),
        Triple("headlineMetric", { t: AppTypography -> t.headlineMetric }, false),
        Triple("titleLarge", { t: AppTypography -> t.titleLarge }, false),
        Triple("titleMedium", { t: AppTypography -> t.titleMedium }, false),
        Triple("bodyLarge", { t: AppTypography -> t.bodyLarge }, false),
        Triple("bodyMedium", { t: AppTypography -> t.bodyMedium }, false),
        Triple("bodySmall", { t: AppTypography -> t.bodySmall }, false),
        Triple("labelNumeric", { t: AppTypography -> t.labelNumeric }, false),
        Triple("labelTechnical", { t: AppTypography -> t.labelTechnical }, true),
    )

/** Catalog section of the theme tokens (T-026): colour swatches and the type scale. Nothing is interactive. */
internal fun LazyListScope.themeItems() {
    item { ThemeSection() }
}

@Composable
private fun ThemeSection() {
    CatalogSection(stringResource(Res.string.catalog_section_theme), "catalog_section_theme") {
        VariantCaption(stringResource(Res.string.catalog_theme_colors))
        AppCard(Modifier.fillMaxWidth().testTag("catalog_theme_colors")) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ColorTokens.forEach { (name, color) -> ColorSwatchRow(name, color(AppTheme.colors)) }
            }
        }
        VariantCaption(stringResource(Res.string.catalog_theme_typography))
        AppCard(Modifier.fillMaxWidth().testTag("catalog_theme_typography")) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TypeStyles.forEach { (name, style, uppercase) ->
                    Text(
                        text = if (uppercase) name.uppercase() else name,
                        style = style(AppTheme.typography),
                        color = AppTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Decorative: the token name next to it is the information.
        Spacer(
            Modifier
                .size(SwatchSize)
                .clip(AppShapes.sm)
                .background(color)
                .border(AppBorder.thin, AppTheme.colors.borderStrong, AppShapes.sm),
        )
        Spacer(Modifier.size(Spacing.md))
        Text(text = name, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textPrimary)
    }
}
