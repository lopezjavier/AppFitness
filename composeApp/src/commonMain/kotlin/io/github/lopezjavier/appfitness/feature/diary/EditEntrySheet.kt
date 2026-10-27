package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.edit_entry_title
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FoodSheet
import io.github.lopezjavier.appfitness.designsystem.FoodSheetContext
import io.github.lopezjavier.appfitness.designsystem.FoodSheetTotals
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import io.github.lopezjavier.appfitness.domain.usecase.BuildLabelNutrients
import io.github.lopezjavier.appfitness.feature.food.LabelNutrientsCard
import io.github.lopezjavier.appfitness.feature.food.MealSelector
import io.github.lopezjavier.appfitness.feature.food.QuantityInput
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private const val SHEET_PREFIX = "food_sheet"
private const val OFFLINE_ACTION = "guardar ni eliminar"
private val SkeletonHeight = Spacing.xl * 2

/**
 * Edit-entry sheet (US-19, US-20) on [FoodSheet] in its Edit context: change the quantity (base unit or portion) and
 * the meal of a logged food, with the live nutrition of the new quantity, "Guardar" and "Eliminar". Saving and
 * deleting need the server, so one offline hint is shown and both are disabled then. [onDismiss] runs when the sheet
 * is closed without saving; a failed save is reported by the caller through the app messages (R-28).
 */
@Composable
fun EditEntrySheet(
    state: EditEntryUiState,
    onEvent: (EditEntryEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        EditEntryUiState.Loading -> AppInfoSheet(
            title = stringResource(Res.string.edit_entry_title),
            onDismiss = onDismiss,
            modifier = modifier,
            testTagPrefix = SHEET_PREFIX,
        ) { SheetLoading() }

        EditEntryUiState.Error -> AppInfoSheet(
            title = stringResource(Res.string.edit_entry_title),
            onDismiss = onDismiss,
            modifier = modifier,
            testTagPrefix = SHEET_PREFIX,
        ) {
            ErrorState(
                message = stringResource(Res.string.edit_entry_load_error),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(EditEntryEvent.Retry) },
                testTagPrefix = "${SHEET_PREFIX}_error_state",
            )
        }

        is EditEntryUiState.Content -> EditEntryContentSheet(state.content, onEvent, onDismiss, modifier)
    }
}

@Composable
private fun SheetLoading() {
    Column(
        Modifier.fillMaxWidth().testTag("${SHEET_PREFIX}_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
private fun EditEntryContentSheet(
    content: EditEntryContent,
    onEvent: (EditEntryEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelNutrients = remember(content.food) { BuildLabelNutrients(content.food.nutrition, Micronutrients.Empty) }
    FoodSheet(
        name = content.food.name,
        brand = content.food.brand,
        quantity = {
            QuantityInput(
                food = content.food,
                amountText = content.amountText,
                selectedPortionId = content.selectedPortionId,
                hasError = content.quantityError != null,
                onAmountChange = { onEvent(EditEntryEvent.AmountChanged(it)) },
                onPortionSelect = { onEvent(EditEntryEvent.PortionSelected(it)) },
                testTagPrefix = SHEET_PREFIX,
            )
        },
        totals = content.preview?.let {
            FoodSheetTotals(wholeHalfUp(it.kcal), amountText(it.proteinG), amountText(it.carbsG), amountText(it.fatG))
        },
        details = {
            LabelNutrientsCard(labelNutrients, content.food.baseUnit, expanded = false, onExpandedChange = {})
        },
        extra = {
            MealSelector(
                slots = content.slots,
                selectedSlotId = content.selectedSlotId,
                onSelect = { onEvent(EditEntryEvent.SlotSelected(it)) },
                containerTag = "${SHEET_PREFIX}_slots",
                optionTagPrefix = "${SHEET_PREFIX}_slot",
                emptyTag = "${SHEET_PREFIX}_no_slot",
            )
        },
        context = FoodSheetContext.Edit(
            canSave = content.canSave,
            saving = content.saving,
            onSave = { onEvent(EditEntryEvent.Save) },
            onDelete = { onEvent(EditEntryEvent.Delete) },
            canDelete = content.canDelete && !content.saving,
            offlineHint = if (content.isOffline) offlineHint(OFFLINE_ACTION) else null,
        ),
        onDismiss = onDismiss,
        modifier = modifier,
        testTagPrefix = SHEET_PREFIX,
    )
}
