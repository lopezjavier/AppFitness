package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_diary_parts
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_short
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.diary_save_template_cd
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_save_meal
import io.github.lopezjavier.appfitness.designsystem.AppTextButton
import io.github.lopezjavier.appfitness.designsystem.AppTextButtonTone
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CompactAddButton
import io.github.lopezjavier.appfitness.designsystem.CompactKcalPill
import io.github.lopezjavier.appfitness.designsystem.CompactTonalIconButton
import io.github.lopezjavier.appfitness.designsystem.EntryTotals
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRowModel
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroLine
import io.github.lopezjavier.appfitness.designsystem.MacroText
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Catalog section of the Diario building blocks: text button, compact meal-card actions, kcal pill, macro texts. */
internal fun LazyListScope.diaryItems() {
    item { DiaryPartsSection() }
}

@Composable
private fun DiaryPartsSection() {
    val saveDescription = stringResource(Res.string.diary_save_template_cd)
    CatalogSection(stringResource(Res.string.catalog_section_diary_parts), "catalog_section_diary_parts") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AppTextButton(
                text = stringResource(Res.string.action_delete),
                onClick = {},
                modifier = Modifier.testTag("catalog_text_button_danger"),
                tone = AppTextButtonTone.Danger,
                leadingIcon = Res.drawable.ic_delete,
            )
            AppTextButton(
                text = stringResource(Res.string.action_delete),
                onClick = {},
                modifier = Modifier.testTag("catalog_text_button_disabled"),
                enabled = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CompactTonalIconButton(
                contentDescription = saveDescription,
                icon = Res.drawable.ic_save_meal,
                testTag = "catalog_compact_tonal_icon",
                onClick = {},
            )
            CompactAddButton(
                text = stringResource(Res.string.diary_add_short),
                contentDescription = stringResource(Res.string.diary_add_short),
                onClick = {},
                modifier = Modifier.testTag("catalog_compact_add"),
            )
            CompactKcalPill(
                text = stringResource(Res.string.diary_meal_kcal, "620"),
                modifier = Modifier.testTag("catalog_compact_kcal_pill"),
            )
        }
        MacroLine(
            proteinG = "48",
            carbsG = "71",
            fatG = "7",
            modifier = Modifier.testTag("catalog_macro_line"),
        )
        MacroText(
            format = Res.string.diary_meal_protein,
            grams = "48",
            macro = Macro.Protein,
            style = AppTheme.typography.bodySmall,
            modifier = Modifier.testTag("catalog_macro_text"),
        )
        EntryTotals(FoodEntryRowModel("catalog", "", "", kcal = 620, proteinG = 48, carbsG = 71, fatG = 7))
    }
}
