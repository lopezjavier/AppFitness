package io.github.lopezjavier.appfitness.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.designsystem.CookingMethodSelector
import io.github.lopezjavier.appfitness.designsystem.FoodSheet
import io.github.lopezjavier.appfitness.designsystem.FoodSheetContext
import io.github.lopezjavier.appfitness.designsystem.FoodSheetTotals
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.CookingMethod
import io.github.lopezjavier.appfitness.feature.diary.amountText
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import io.github.lopezjavier.appfitness.feature.food.CookingImpactLine
import io.github.lopezjavier.appfitness.feature.food.MicronutrientsCard
import io.github.lopezjavier.appfitness.feature.food.QuantityInput

private const val SHEET_PREFIX = "food_sheet"

/**
 * One food of the comida being edited, on [FoodSheet] in its Comida context: its amount in the base unit (pending until
 * "Listo" applies it to the editor's draft, which "Guardar" then persists), the cooking method, the live totals, its
 * micronutrients when known, "Listo" and "Quitar de la comida" (disabled for the last food; it only stages the
 * removal). The X, back and drag discard the typed amount.
 */
@Suppress("LongParameterList") // One callback per action of the sheet.
@Composable
internal fun TemplateFoodSheet(
    open: OpenFood,
    onAmountChange: (String) -> Unit,
    onMethodChange: (CookingMethod) -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val row = open.row
    // Hoisted above the sheet window: state declared inside a sheet is not restored.
    var microExpanded by rememberSaveable { mutableStateOf(false) }
    FoodSheet(
        name = row.name,
        brand = null,
        quantity = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                QuantityInput(
                    food = open.food.copy(portions = emptyList()),
                    amountText = row.amountText,
                    selectedPortionId = null,
                    hasError = row.quantityError != null,
                    onAmountChange = onAmountChange,
                    onPortionSelect = {},
                    testTagPrefix = SHEET_PREFIX,
                )
                if (row.canCook) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CookingMethodSelector(
                            selected = row.cookingMethod,
                            onSelect = onMethodChange,
                            tagPrefix = "${SHEET_PREFIX}_cooking",
                        )
                        row.cookingImpact?.let {
                            CookingImpactLine(it, Modifier.testTag("${SHEET_PREFIX}_cooking_impact"))
                        }
                    }
                }
            }
        },
        totals = FoodSheetTotals(
            wholeHalfUp(row.kcal),
            amountText(row.proteinG),
            amountText(row.carbsG),
            amountText(row.fatG),
        ),
        details = open.micronutrients?.let { micros ->
            {
                MicronutrientsCard(micros, open.food.baseUnit, microExpanded, onExpandedChange = { microExpanded = it })
            }
        },
        extra = null,
        context = FoodSheetContext.Comida(
            onDone = onDone,
            onRemove = onRemove,
            canRemove = row.removable,
            canDone = open.canSave,
        ),
        onDismiss = onDismiss,
        testTagPrefix = SHEET_PREFIX,
    )
}
