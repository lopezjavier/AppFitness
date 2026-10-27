package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_amount_label_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_amount_label_ml
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_meal_label
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_no_meal_hint
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_photo_cd
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_portion_count_label
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_portion_option
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_preview_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_quantity_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_sheet_title
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_unit_ml
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.ic_photo_camera
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppInfoSheet
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CookingMethodSelector
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.FoodSheet
import io.github.lopezjavier.appfitness.designsystem.FoodSheetContext
import io.github.lopezjavier.appfitness.designsystem.FoodSheetTotals
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.PortionOption
import io.github.lopezjavier.appfitness.designsystem.PortionSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.feature.diary.amountText
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoEvent
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoNotice
import io.github.lopezjavier.appfitness.feature.food.photo.AmountPhotoUiState
import io.github.lopezjavier.appfitness.feature.food.photo.EstimateConfidenceLine
import io.github.lopezjavier.appfitness.feature.food.photo.FoodPhotoStep
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_ROWS = 3
private const val SHEET_PREFIX = "food_sheet"
private const val OFFLINE_ACTION = "añadir"
private val SkeletonHeight = Spacing.xl * 2

/**
 * Add-food bottom sheet (US-17, US-22) on [FoodSheet] in its Add context: the quantity first (base unit or named
 * portion), the cooking method, the live totals of that quantity, the nutrients per 100 base units collapsed under
 * "Detalles", the meal chips and the pinned "Añadir". It only needs a [Food] and a preselected meal, so the barcode,
 * create-food and template flows can reuse it. A failed add is reported by the caller through the app messages (R-28).
 * With [amountPhoto] set, a photo icon in the amount field starts the estimate flow (US-54); "Estimando…" and its
 * failure show inside the sheet, while the source sheet and the preview come from `AmountPhotoFlow`. This sheet is
 * not composed while that full-screen preview is on screen.
 */
@Composable
fun AddFoodSheet(
    state: AddFoodUiState,
    onEvent: (AddFoodEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    amountPhoto: AmountPhotoUiState? = null,
    onAmountPhotoEvent: (AmountPhotoEvent) -> Unit = {},
) {
    if (amountPhoto?.step is FoodPhotoStep.Preview) return
    when (state) {
        AddFoodUiState.Loading -> AppInfoSheet(
            title = stringResource(Res.string.add_food_sheet_title),
            onDismiss = onDismiss,
            modifier = modifier,
            testTagPrefix = SHEET_PREFIX,
        ) { SheetLoading() }

        AddFoodUiState.Error -> AppInfoSheet(
            title = stringResource(Res.string.add_food_sheet_title),
            onDismiss = onDismiss,
            modifier = modifier,
            testTagPrefix = SHEET_PREFIX,
        ) {
            ErrorState(
                message = stringResource(Res.string.add_food_load_error),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(AddFoodEvent.Retry) },
                testTagPrefix = "${SHEET_PREFIX}_error_state",
            )
        }

        is AddFoodUiState.Content -> AddFoodContentSheet(
            content = state.content,
            onEvent = onEvent,
            onDismiss = onDismiss,
            onEdit = onEdit,
            amountPhoto = amountPhoto,
            onAmountPhotoEvent = onAmountPhotoEvent,
            modifier = modifier,
        )
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
@Suppress("LongParameterList") // The content, its events, the edit action and the photo flow.
private fun AddFoodContentSheet(
    content: AddFoodContent,
    onEvent: (AddFoodEvent) -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    amountPhoto: AmountPhotoUiState?,
    onAmountPhotoEvent: (AmountPhotoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted above the sheet window: state declared inside a sheet is not restored.
    var microExpanded by rememberSaveable { mutableStateOf(false) }
    FoodSheet(
        name = content.food.name,
        brand = content.food.brand,
        quantity = { QuantityBlock(content, onEvent, amountPhoto, onAmountPhotoEvent) },
        totals = content.preview?.let {
            FoodSheetTotals(wholeHalfUp(it.kcal), amountText(it.proteinG), amountText(it.carbsG), amountText(it.fatG))
        },
        details = {
            LabelNutrientsCard(content.labelNutrients, content.food.baseUnit, microExpanded, { microExpanded = it })
        },
        extra = if (content.pick) {
            null
        } else {
            {
                MealSelector(
                    slots = content.slots,
                    selectedSlotId = content.selectedSlotId,
                    onSelect = { onEvent(AddFoodEvent.SlotSelected(it)) },
                    containerTag = "${SHEET_PREFIX}_meals",
                    optionTagPrefix = "${SHEET_PREFIX}_meal",
                    emptyTag = "${SHEET_PREFIX}_no_meal",
                )
            }
        },
        context = FoodSheetContext.Add(
            canAdd = content.canSubmit,
            saving = content.submitting,
            onAdd = { onEvent(AddFoodEvent.Submit) },
            offlineHint = if (content.isOffline && !content.pick) offlineHint(OFFLINE_ACTION) else null,
        ),
        onDismiss = onDismiss,
        modifier = modifier,
        onEditFood = if (content.showEdit) onEdit else null,
        editFoodEnabled = content.canEdit,
        testTagPrefix = SHEET_PREFIX,
    )
}

/** The quantity, what the photo flow says about it, and the cooking method with its impact. */
@Composable
private fun QuantityBlock(
    content: AddFoodContent,
    onEvent: (AddFoodEvent) -> Unit,
    amountPhoto: AmountPhotoUiState?,
    onAmountPhotoEvent: (AmountPhotoEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        QuantityInput(
            food = content.food,
            amountText = content.amountText,
            selectedPortionId = content.selectedPortionId,
            hasError = content.quantityError != null,
            onAmountChange = { onEvent(AddFoodEvent.AmountChanged(it)) },
            onPortionSelect = { onEvent(AddFoodEvent.PortionSelected(it)) },
            testTagPrefix = SHEET_PREFIX,
            onPhotoClick = amountPhoto?.let { { onAmountPhotoEvent(AmountPhotoEvent.Open(content.amountText)) } },
            photoEnabled = amountPhoto?.isOffline == false,
        )
        content.estimateConfidence?.let { EstimateConfidenceLine(it) }
        amountPhoto?.let { AmountPhotoNotice(it, onAmountPhotoEvent) }
        if (content.showCooking) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CookingMethodSelector(
                    selected = content.cookingMethod,
                    onSelect = { onEvent(AddFoodEvent.CookingMethodSelected(it)) },
                    tagPrefix = "${SHEET_PREFIX}_cooking",
                )
                content.cookingImpact?.let {
                    CookingImpactLine(it, Modifier.testTag("${SHEET_PREFIX}_cooking_impact"))
                }
            }
        }
    }
}

@Composable
internal fun KcalText(kcal: Double, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.add_food_preview_kcal, wholeHalfUp(kcal)),
        style = AppTheme.typography.labelNumeric,
        color = AppTheme.colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
internal fun MacroTexts(proteinG: Double, carbsG: Double, fatG: Double) {
    val colors = AppTheme.colors
    val style = AppTheme.typography.labelNumeric
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            stringResource(Res.string.diary_meal_protein, amountText(proteinG)),
            style = style,
            color = MacroDefaults.color(Macro.Protein, colors),
        )
        Text(
            stringResource(Res.string.diary_meal_carbs, amountText(carbsG)),
            style = style,
            color = MacroDefaults.color(Macro.Carbs, colors),
        )
        Text(
            stringResource(Res.string.diary_meal_fat, amountText(fatG)),
            style = style,
            color = MacroDefaults.color(Macro.Fat, colors),
        )
    }
}

/**
 * The quantity field of [food]: base unit or named portion. Tags derive from [testTagPrefix] (see
 * [PortionSelector]); shared by the add-food, edit-entry and comida-item sheets. A photo icon (US-54) sits after the
 * field only when [onPhotoClick] is set (`<prefix>_amount_photo`); it is disabled when [photoEnabled] is false.
 */
@Composable
@Suppress("LongParameterList") // The field's value, its events and its tags.
internal fun QuantityInput(
    food: Food,
    amountText: String,
    selectedPortionId: String?,
    hasError: Boolean,
    onAmountChange: (String) -> Unit,
    onPortionSelect: (String?) -> Unit,
    testTagPrefix: String,
    onPhotoClick: (() -> Unit)? = null,
    photoEnabled: Boolean = true,
) {
    val unit = food.baseUnit
    val inMl = unit == "ml"
    val portions = food.portions.map {
        val amount = amountText(it.amount)
        val formatted = stringResource(Res.string.add_food_portion_option, it.label, amount, unit)
        PortionOption(it.id, portionOptionLabel(it.label, amount, unit, formatted))
    }
    val amountLabel = stringResource(
        when {
            selectedPortionId != null -> Res.string.add_food_portion_count_label
            inMl -> Res.string.add_food_amount_label_ml
            else -> Res.string.add_food_amount_label_g
        },
    )
    PortionSelector(
        amountText = amountText,
        onAmountChange = onAmountChange,
        amountLabel = amountLabel,
        baseLabel = stringResource(if (inMl) Res.string.add_food_unit_ml else Res.string.add_food_unit_g),
        portions = portions,
        selectedPortionId = selectedPortionId,
        onPortionSelect = onPortionSelect,
        errorText = if (hasError) stringResource(Res.string.add_food_quantity_error) else null,
        testTagPrefix = testTagPrefix,
        amountTrailing = onPhotoClick?.let { click ->
            {
                AppIconButton(
                    contentDescription = stringResource(Res.string.add_food_photo_cd),
                    icon = Res.drawable.ic_photo_camera,
                    testTag = "${testTagPrefix}_amount_photo",
                    onClick = click,
                    enabled = photoEnabled,
                )
            }
        },
    )
}

/**
 * The meal choice (SEL-2): one single-select chip per meal, any number of meals. [containerTag] tags the group,
 * `<optionTagPrefix>_<slotId>` each chip and [emptyTag] the hint shown when there is no meal.
 */
@Composable
@Suppress("LongParameterList") // The choice, its event and the three tags.
internal fun MealSelector(
    slots: List<MealSlot>,
    selectedSlotId: String?,
    onSelect: (String) -> Unit,
    containerTag: String,
    optionTagPrefix: String,
    emptyTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.add_food_meal_label),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        if (slots.isEmpty()) {
            Text(
                text = stringResource(Res.string.add_food_no_meal_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag(emptyTag),
            )
            return@Column
        }
        val options = slots.map { AppOption(it.label, "${optionTagPrefix}_${it.id}") }
        val selected = slots.indexOfFirst { it.id == selectedSlotId }.takeIf { it >= 0 }
        FilterChipRow(options, selected, { onSelect(slots[it].id) }, Modifier.testTag(containerTag))
    }
}
