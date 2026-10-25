@file:Suppress("TooManyFunctions") // One composable per block of the form.

package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_ai_note
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_barcode
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_brand
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_carbs_total
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_negative
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_not_a_number
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_required
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_too_large
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_fat_total
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_fiber
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_kcal_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_kcal_ml
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_load_failed
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_name
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_protein
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_salt
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_saturated_of
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_sugars_of
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_locked
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_ml
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_refused
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import org.jetbrains.compose.resources.stringResource

private const val MAX_NAME_LENGTH = 200
private const val NUTRIENT_DECIMALS = 2
private const val LABEL_DECIMALS = 4
private const val GRID_COLUMNS = 2
private const val LARGE_FONT_SCALE = 1.3f
private const val SKELETON_ROWS = 4
private val SkeletonHeight = Spacing.xl * 2

/**
 * Create / edit food form (US-18, US-22, US-47): name, brand, base unit, nutrition per 100, the label nutrients and
 * named portions. The top bar belongs to the shell; "Guardar" is anchored below the scrolling form, above the
 * keyboard. In edit mode the form shows a skeleton while loading and a retry when the food could not be read.
 */
@Composable
fun CreateFoodScreen(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("create_food_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize().imePadding()) {
            when {
                state.loadFailed -> ErrorState(
                    message = stringResource(Res.string.create_food_load_failed),
                    action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(CreateFoodEvent.Retry) },
                    modifier = Modifier.padding(Spacing.screenMargin),
                    testTagPrefix = "create_food_retry",
                )

                state.isLoading -> FormLoading()

                else -> {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                    ) {
                        if (state.aiEstimateNote) {
                            InlineNote(
                                text = stringResource(Res.string.create_food_ai_note),
                                testTag = "create_food_ai_note",
                            )
                        }
                        IdentityBlock(state, onEvent)
                        NutritionBlock(state, onEvent)
                        PortionsBlock(state, onEvent)
                        CreateFoodSection.entries.forEach { SectionBlock(it, state, onEvent) }
                    }
                    SaveBar(state, onEvent)
                }
            }
        }
    }
}

@Composable
private fun FormLoading() {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin).testTag("create_food_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().heightIn(min = SkeletonHeight)) }
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
        UnitSection(state, onEvent)
    }
}

@Composable
private fun UnitSection(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
            enabled = !state.unitLocked,
        )
        // The refusal replaces the hint: never both at once.
        if (state.unitRefused) {
            Text(
                text = stringResource(Res.string.create_food_unit_refused),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("create_food_unit_refused"),
            )
        } else if (state.unitLocked) {
            Text(
                text = stringResource(Res.string.create_food_unit_locked),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("create_food_unit_locked"),
            )
        }
    }
}

/** Visible label, macro colour (null = neutral), test tag, spoken label and decimals of a nutrient field. */
internal class NutrientSpec(
    val label: String,
    val macro: Macro?,
    val tag: String,
    val spokenLabel: String = label,
    val decimals: Int = NUTRIENT_DECIMALS,
)

/**
 * The nutrients in one column, in label order (US-18, US-25): calories per 100, total fat with its saturated part
 * indented, carbohydrates with the sugars indented, fibre, protein and salt. Fat and saturated share the fat colour,
 * carbohydrates and sugars the carbs colour; fibre and salt are neutral.
 */
@Composable
private fun NutritionBlock(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    val errors = state.errors
    fun change(field: CreateFoodField): (String) -> Unit = { onEvent(CreateFoodEvent.FieldChanged(field, it)) }

    @Composable
    fun KindField(kind: MicronutrientKind, label: String, macro: Macro?, indent: Boolean = false) {
        val spec =
            NutrientSpec(label, macro, "create_food_nutrient_${micronutrientTag(kind)}", decimals = LABEL_DECIMALS)
        Box(if (indent) Modifier.padding(start = Spacing.lg) else Modifier) {
            NutrientField(spec, state.nutrients[kind].orEmpty(), errors.nutrients[kind]) {
                onEvent(CreateFoodEvent.NutrientChanged(kind, it))
            }
        }
    }
    val kcalLabel = stringResource(
        if (state.baseUnit == "ml") Res.string.create_food_kcal_ml else Res.string.create_food_kcal_g,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        NutrientField(
            NutrientSpec(kcalLabel, null, "create_food_kcal_field"),
            state.kcal,
            errors.kcal,
            change(CreateFoodField.Kcal),
        )
        val fat = NutrientSpec(stringResource(Res.string.create_food_fat_total), Macro.Fat, "create_food_fat_field")
        NutrientField(fat, state.fat, errors.fat, change(CreateFoodField.Fat))
        KindField(MicronutrientKind.SaturatedFat, stringResource(Res.string.create_food_saturated_of), Macro.Fat, true)
        val carbs =
            NutrientSpec(stringResource(Res.string.create_food_carbs_total), Macro.Carbs, "create_food_carbs_field")
        NutrientField(carbs, state.carbs, errors.carbs, change(CreateFoodField.Carbs))
        KindField(MicronutrientKind.Sugars, stringResource(Res.string.create_food_sugars_of), Macro.Carbs, true)
        KindField(MicronutrientKind.Fiber, stringResource(Res.string.create_food_fiber), null)
        val protein =
            NutrientSpec(stringResource(Res.string.create_food_protein), Macro.Protein, "create_food_protein_field")
        NutrientField(protein, state.protein, errors.protein, change(CreateFoodField.Protein))
        KindField(MicronutrientKind.Salt, stringResource(Res.string.create_food_salt), null)
    }
}

/** Fields in rows of two; one per row when the font is large enough to squeeze a field below ~140 dp. */
@Composable
internal fun FieldGrid(fields: List<@Composable () -> Unit>) {
    val columns = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) 1 else GRID_COLUMNS
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        fields.chunked(columns).forEach { rowFields ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rowFields.forEach { Box(Modifier.weight(1f)) { it() } }
                repeat(columns - rowFields.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A decimal field with its label (in the macro colour when set) and the error text under it. */
@Composable
internal fun NutrientField(spec: NutrientSpec, value: String, error: NewFoodError?, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = spec.label,
            style = AppTheme.typography.bodyMedium,
            color = spec.macro?.let { MacroDefaults.color(it, AppTheme.colors) } ?: AppTheme.colors.textSecondary,
        )
        NumberInput(
            value = value,
            onValueChange = onChange,
            label = spec.spokenLabel,
            modifier = Modifier.fillMaxWidth().testTag(spec.tag),
            maxDecimals = spec.decimals,
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
