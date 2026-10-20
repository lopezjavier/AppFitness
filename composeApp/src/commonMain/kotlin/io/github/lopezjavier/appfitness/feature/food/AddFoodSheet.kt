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

                is AddFoodUiState.Content -> SheetContent(state.content, onEvent, submitFailed)
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
private fun SheetContent(content: AddFoodContent, onEvent: (AddFoodEvent) -> Unit, submitFailed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        FoodHeader(content.food)
        NutritionPer100Card(content.food)
        QuantityInput(content, onEvent)
        content.preview?.let { Preview(it.kcal, it.proteinG, it.carbsG, it.fatG) }
        MealSelector(content, onEvent)
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
private fun KcalText(kcal: Double, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.add_food_preview_kcal, wholeHalfUp(kcal)),
        style = AppTheme.typography.labelNumeric,
        color = AppTheme.colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
private fun MacroTexts(proteinG: Double, carbsG: Double, fatG: Double) {
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

@Composable
private fun QuantityInput(content: AddFoodContent, onEvent: (AddFoodEvent) -> Unit) {
    val unit = content.food.baseUnit
    val inMl = unit == "ml"
    val portions = content.food.portions.map {
        PortionOption(it.id, stringResource(Res.string.add_food_portion_option, it.label, amountText(it.amount), unit))
    }
    val amountLabel = stringResource(
        when {
            content.selectedPortionId != null -> Res.string.add_food_portion_count_label
            inMl -> Res.string.add_food_amount_label_ml
            else -> Res.string.add_food_amount_label_g
        },
    )
    PortionSelector(
        amountText = content.amountText,
        onAmountChange = { onEvent(AddFoodEvent.AmountChanged(it)) },
        amountLabel = amountLabel,
        baseLabel = stringResource(if (inMl) Res.string.add_food_unit_ml else Res.string.add_food_unit_g),
        portions = portions,
        selectedPortionId = content.selectedPortionId,
        onPortionSelect = { onEvent(AddFoodEvent.PortionSelected(it)) },
        errorText = content.quantityError?.let { stringResource(Res.string.add_food_quantity_error) },
        testTagPrefix = "add_food",
    )
}

/** The nutrition of the typed quantity; a polite live region so a screen reader announces the new total. */
@Composable
private fun Preview(kcal: Double, proteinG: Double, carbsG: Double, fatG: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("add_food_preview"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KcalText(kcal, Modifier.testTag("add_food_preview_kcal"))
        MacroTexts(proteinG, carbsG, fatG)
    }
}

@Composable
private fun MealSelector(content: AddFoodContent, onEvent: (AddFoodEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(Res.string.add_food_meal_label),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        if (content.slots.isEmpty()) {
            Text(
                text = stringResource(Res.string.add_food_no_meal_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier.testTag("add_food_no_meal"),
            )
            return@Column
        }
        val options = content.slots.map { AppOption(it.label, "add_food_meal_${it.id}") }
        val selected = content.slots.indexOfFirst { it.id == content.selectedSlotId }.takeIf { it >= 0 }
        val select = { index: Int -> onEvent(AddFoodEvent.SlotSelected(content.slots[index].id)) }
        if (content.slots.size <= MAX_SEGMENTED_SLOTS) {
            SegmentedSelector(options, selected, select, Modifier.testTag("add_food_meals"))
        } else {
            FilterChipRow(options, selected, select, Modifier.testTag("add_food_meals"))
        }
    }
}
