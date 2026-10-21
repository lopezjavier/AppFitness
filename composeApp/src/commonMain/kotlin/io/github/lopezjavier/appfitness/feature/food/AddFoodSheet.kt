@file:OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.

package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_amount_label_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_amount_label_ml
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_meal_label
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_no_meal_hint
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_ml
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_portion_count_label
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_portion_option
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_preview_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_quantity_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_submit
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_submit_error
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_unit_ml
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.PortionOption
import io.github.lopezjavier.appfitness.designsystem.PortionSelector
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.Food
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.feature.diary.amountText
import io.github.lopezjavier.appfitness.feature.diary.wholeHalfUp
import org.jetbrains.compose.resources.stringResource

private const val MAX_SEGMENTED_SLOTS = 4
private const val SKELETON_ROWS = 3
private val SkeletonHeight = Spacing.xl * 2

/**
 * Add-food bottom sheet (US-17, US-22): the nutrition of 100 base units, the quantity (base unit or named portion),
 * the live nutrition of that quantity, the meal and "Añadir". It only needs a [Food] and a preselected meal, so the
 * barcode, create-food and template flows can reuse it. [submitFailed] shows the logging error under the button.
 */
@Composable
fun AddFoodSheet(
    state: AddFoodUiState,
    onEvent: (AddFoodEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    submitFailed: Boolean = false,
) {
    // Hoisted here (not in the card) so it survives the card leaving while the sheet reloads.
    var microExpanded by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = "add_food_sheet") {
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin)
                .padding(bottom = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            when (state) {
                AddFoodUiState.Loading -> SheetLoading()

                AddFoodUiState.Error -> ErrorState(
                    message = stringResource(Res.string.add_food_load_error),
                    action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(AddFoodEvent.Retry) },
                    testTagPrefix = "add_food_error_state",
                )

                is AddFoodUiState.Content ->
                    SheetContent(state.content, onEvent, submitFailed, microExpanded) { microExpanded = it }
            }
        }
    }
}

@Composable
private fun SheetLoading() {
    Column(
        Modifier.fillMaxWidth().testTag("add_food_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
    }
}

@Composable
private fun SheetContent(
    content: AddFoodContent,
    onEvent: (AddFoodEvent) -> Unit,
    submitFailed: Boolean,
    microExpanded: Boolean,
    onMicroExpandedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        FoodHeader(content.food)
        NutritionPer100Card(content.food)
        (content.micronutrients as? MicronutrientsState.Shown)?.let {
            MicronutrientsCard(it.micronutrients, content.food.baseUnit, microExpanded, onMicroExpandedChange)
        }
        QuantityInput(
            food = content.food,
            amountText = content.amountText,
            selectedPortionId = content.selectedPortionId,
            hasError = content.quantityError != null,
            onAmountChange = { onEvent(AddFoodEvent.AmountChanged(it)) },
            onPortionSelect = { onEvent(AddFoodEvent.PortionSelected(it)) },
            testTagPrefix = "add_food",
        )
        content.preview?.let { Preview(it.kcal, it.proteinG, it.carbsG, it.fatG, "add_food_preview") }
        MealSelector(
            slots = content.slots,
            selectedSlotId = content.selectedSlotId,
            onSelect = { onEvent(AddFoodEvent.SlotSelected(it)) },
            containerTag = "add_food_meals",
            optionTagPrefix = "add_food_meal",
            emptyTag = "add_food_no_meal",
        )
        if (submitFailed) {
            Text(
                text = stringResource(Res.string.add_food_submit_error),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("add_food_submit_error"),
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.add_food_submit),
            onClick = { onEvent(AddFoodEvent.Submit) },
            enabled = content.canSubmit,
            loading = content.submitting,
            modifier = Modifier.fillMaxWidth().testTag("add_food_submit"),
        )
    }
}

@Composable
private fun FoodHeader(food: Food) {
    Column {
        Text(
            text = food.name,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("add_food_name"),
        )
        food.brand?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun NutritionPer100Card(food: Food) {
    val unit = food.baseUnit
    AppCard(Modifier.fillMaxWidth().testTag("add_food_nutrition")) {
        Text(
            text = stringResource(
                if (unit == "ml") Res.string.add_food_nutrition_per_100_ml else Res.string.add_food_nutrition_per_100_g,
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KcalText(food.nutrition.kcal)
            MacroTexts(food.nutrition.proteinG, food.nutrition.carbsG, food.nutrition.fatG)
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
 * [PortionSelector]); shared by the add-food and edit-entry sheets.
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
    )
}

/** The nutrition of the typed quantity; a polite live region so a screen reader announces the new total. */
@Composable
internal fun Preview(kcal: Double, proteinG: Double, carbsG: Double, fatG: Double, tag: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KcalText(kcal, Modifier.testTag("${tag}_kcal"))
        MacroTexts(proteinG, carbsG, fatG)
    }
}

/**
 * The meal choice: segments up to [MAX_SEGMENTED_SLOTS] meals, chips beyond. [containerTag] tags the control,
 * `<optionTagPrefix>_<slotId>` each option and [emptyTag] the hint shown when there is no meal.
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
        val select = { index: Int -> onSelect(slots[index].id) }
        if (slots.size <= MAX_SEGMENTED_SLOTS) {
            SegmentedSelector(options, selected, select, Modifier.testTag(containerTag))
        } else {
            FilterChipRow(options, selected, select, Modifier.testTag(containerTag))
        }
    }
}
