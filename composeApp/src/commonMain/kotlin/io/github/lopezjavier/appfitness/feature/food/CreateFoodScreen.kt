package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_barcode
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_brand
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_negative
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_not_a_number
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_required
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_too_large
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_fat
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_kcal_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_kcal_ml
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_name
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_protein
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_ml
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import org.jetbrains.compose.resources.stringResource

private const val MAX_NAME_LENGTH = 200
private const val NUTRIENT_DECIMALS = 2

/**
 * Create-food form (US-18, US-22): name, brand, base unit, nutrition per 100 and named portions. The top bar belongs
 * to the shell; "Guardar" is anchored below the scrolling form, above the keyboard.
 */
@Composable
fun CreateFoodScreen(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("create_food_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize().imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                IdentityBlock(state, onEvent)
                NutritionBlock(state, onEvent)
                PortionsBlock(state, onEvent)
            }
            SaveBar(state, onEvent)
        }
    }
}

@Composable
private fun IdentityBlock(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        AppTextField(
            value = state.name,
            onValueChange = { onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Name, it)) },
            label = stringResource(Res.string.create_food_name),
            maxLength = MAX_NAME_LENGTH,
            errorText = state.errors.name?.let { errorMessage(it) },
            testTagPrefix = "create_food_name_field",
        )
        AppTextField(
            value = state.brand,
            onValueChange = { onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Brand, it)) },
            label = stringResource(Res.string.create_food_brand),
            maxLength = MAX_NAME_LENGTH,
            errorText = state.errors.brand?.let { errorMessage(it) },
            testTagPrefix = "create_food_brand_field",
        )
        state.barcode?.let {
            Text(
                text = "${stringResource(Res.string.create_food_barcode)}: $it",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("create_food_barcode_text"),
            )
        }
        Text(
            text = stringResource(Res.string.create_food_unit),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        val options = listOf(
            AppOption(stringResource(Res.string.create_food_unit_g), "create_food_unit_g"),
            AppOption(stringResource(Res.string.create_food_unit_ml), "create_food_unit_ml"),
        )
        SegmentedSelector(
            options = options,
            selectedIndex = if (state.baseUnit == "ml") 1 else 0,
            onSelect = { onEvent(CreateFoodEvent.UnitSelected(if (it == 1) "ml" else "g")) },
            modifier = Modifier.testTag("create_food_unit_selector"),
        )
    }
}

/** Label, macro colour (null = neutral) and test tag of a nutrient field. */
private class NutrientSpec(val label: String, val macro: Macro?, val tag: String)

@Composable
private fun NutritionBlock(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    val kcalLabel = stringResource(
        if (state.baseUnit == "ml") Res.string.create_food_kcal_ml else Res.string.create_food_kcal_g,
    )
    val errors = state.errors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NutrientField(NutrientSpec(kcalLabel, null, "create_food_kcal_field"), state.kcal, errors.kcal) {
            onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Kcal, it))
        }
        val protein =
            NutrientSpec(stringResource(Res.string.create_food_protein), Macro.Protein, "create_food_protein_field")
        NutrientField(protein, state.protein, errors.protein) {
            onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Protein, it))
        }
        val carbs = NutrientSpec(stringResource(Res.string.create_food_carbs), Macro.Carbs, "create_food_carbs_field")
        NutrientField(carbs, state.carbs, errors.carbs) {
            onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Carbs, it))
        }
        val fat = NutrientSpec(stringResource(Res.string.create_food_fat), Macro.Fat, "create_food_fat_field")
        NutrientField(fat, state.fat, errors.fat) { onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Fat, it)) }
    }
}

/** A decimal field with its label (in the macro colour when [macro] is set) and the error text under it. */
@Composable
private fun NutrientField(spec: NutrientSpec, value: String, error: NewFoodError?, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = spec.label,
            style = AppTheme.typography.bodyMedium,
            color = spec.macro?.let { MacroDefaults.color(it, AppTheme.colors) } ?: AppTheme.colors.textSecondary,
        )
        NumberInput(
            value = value,
            onValueChange = onChange,
            label = spec.label,
            modifier = Modifier.fillMaxWidth().testTag(spec.tag),
            maxDecimals = NUTRIENT_DECIMALS,
            showLabel = false,
        )
        error?.let { FieldErrorText(errorMessage(it), "${spec.tag}_error") }
    }
}

@Composable
internal fun FieldErrorText(text: String, tag: String) {
    Text(
        text = text,
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
    )
}

@Composable
private fun SaveBar(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenMargin, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.isOffline) {
            Text(
                text = stringResource(Res.string.create_food_offline_hint),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("create_food_offline_hint"),
            )
        }
        if (state.saveFailed) {
            FieldErrorText(stringResource(Res.string.create_food_save_error), "create_food_save_error")
        }
        PrimaryButton(
            text = stringResource(Res.string.create_food_save),
            onClick = { onEvent(CreateFoodEvent.Save) },
            modifier = Modifier.fillMaxWidth().testTag("create_food_save_button"),
            enabled = state.canSave,
            loading = state.isSaving,
        )
    }
}

@Composable
private fun errorMessage(error: NewFoodError): String = stringResource(
    when (error) {
        NewFoodError.Required -> Res.string.create_food_error_required
        NewFoodError.TooLong -> Res.string.create_food_error_too_long
        NewFoodError.Negative -> Res.string.create_food_error_negative
        NewFoodError.TooLarge -> Res.string.create_food_error_too_large
        NewFoodError.NotANumber -> Res.string.create_food_error_not_a_number
    },
)
