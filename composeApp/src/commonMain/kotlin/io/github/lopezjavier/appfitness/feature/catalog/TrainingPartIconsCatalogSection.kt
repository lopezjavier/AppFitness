package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_arms
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_core
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_full_body
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_generic
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_glutes
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_lower
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_pull
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_push
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_part_upper
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_training_part_icons
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrainingPartGlyph
import io.github.lopezjavier.appfitness.designsystem.TrainingPartIcon
import io.github.lopezjavier.appfitness.domain.model.RoutinePart
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the training-part icons (T-306, 05 §6.0j): ten glyphs, lime and cyan, 24 dp and enlarged. */
internal fun LazyListScope.trainingPartIconItems() {
    item { TrainingPartIconsSection() }
}

private val PartIcons: List<Pair<RoutinePart, StringResource>> = listOf(
    RoutinePart.FullBody to Res.string.catalog_part_full_body,
    RoutinePart.Upper to Res.string.catalog_part_upper,
    RoutinePart.Lower to Res.string.catalog_part_lower,
    RoutinePart.Push to Res.string.catalog_part_push,
    RoutinePart.Pull to Res.string.catalog_part_pull,
    RoutinePart.Arms to Res.string.catalog_part_arms,
    RoutinePart.Glutes to Res.string.catalog_part_glutes,
    RoutinePart.Core to Res.string.catalog_part_core,
    RoutinePart.Cardio to Res.string.catalog_part_cardio,
    RoutinePart.Generic to Res.string.catalog_part_generic,
)

private val EnlargedSize = Spacing.xl * 2

@Composable
private fun TrainingPartIconsSection() {
    val colors = AppTheme.colors
    CatalogSection(
        stringResource(Res.string.catalog_section_training_part_icons),
        "catalog_section_training_part_icons",
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                listOf(colors.neonLime, colors.athleticCyan).forEach { tint ->
                    PartIconRows(tint)
                }
            }
        }
    }
}

@Composable
private fun PartIconRows(tint: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PartIcons.forEach { (part, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TrainingPartIcon(part, isNext = tint == AppTheme.colors.athleticCyan)
                    Text(stringResource(label), color = AppTheme.colors.textSecondary)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PartIcons.forEach { (part, _) ->
                TrainingPartGlyph(part, size = EnlargedSize, tint = tint)
            }
        }
    }
}
