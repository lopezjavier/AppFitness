package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_item_amount
import io.github.lopezjavier.appfitness.composeapp.resources.template_details_load_error
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRowModel
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.labelRes
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.feature.diary.FoodEntryDetail
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUpInt
import io.github.lopezjavier.appfitness.feature.food.KcalText
import io.github.lopezjavier.appfitness.feature.food.MacroTexts
import org.jetbrains.compose.resources.stringResource

private const val LOADING_ROWS = 4
private val SkeletonHeight = Spacing.xl * 2

/** The row model of a food of the comida: the amount line from [FoodEntryDetail], whole kcal and macros. */
@Composable
internal fun DetailsRow.toRowModel(): FoodEntryRowModel {
    val cookingLabel = if (cookingMethod == CookingMethod.Raw) null else stringResource(cookingMethod.labelRes())
    return FoodEntryRowModel(
        id = index.toString(),
        name = name,
        detail = FoodEntryDetail.format(portionLabel, amountText.toDoubleOrNull() ?: 0.0, unit, cookingLabel),
        kcal = wholeHalfUpInt(kcal),
        proteinG = wholeHalfUpInt(proteinG),
        carbsG = wholeHalfUpInt(carbsG),
        fatG = wholeHalfUpInt(fatG),
    )
}

/** The total kcal and macros of the comida (the draft in the editor), tagged `<prefix>_kcal` and `_macros`. */
@Composable
internal fun TotalsCard(content: TemplateDetailsUiState.Content, prefix: String) {
    AppCard(Modifier.fillMaxWidth().testTag("${prefix}_totals")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            KcalText(content.totalKcal, Modifier.testTag("${prefix}_kcal"))
            Box(Modifier.testTag("${prefix}_macros")) {
                MacroTexts(content.totalProteinG, content.totalCarbsG, content.totalFatG)
            }
        }
    }
}

@Composable
internal fun DetailsLoading(prefix: String) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("${prefix}_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
internal fun DetailsLoadError(prefix: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.template_details_load_error),
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            testTagPrefix = "${prefix}_error_state",
        )
    }
}

/** Read-only look at one food of the comida (the details screen never applies anything): amount and nutrition. */
@Composable
internal fun TemplateItemInfoSheet(row: DetailsRow, onDismiss: () -> Unit) {
    AppInfoSheet(title = row.name, onDismiss = onDismiss, testTagPrefix = "template_item_sheet") {
        Column(Modifier.padding(bottom = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = stringResource(Res.string.template_details_item_amount, row.amountText, row.unit),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("template_item_sheet_amount"),
            )
            Row(
                Modifier.testTag("template_item_sheet_totals"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KcalText(row.kcal)
                MacroTexts(row.proteinG, row.carbsG, row.fatG)
            }
        }
    }
}
