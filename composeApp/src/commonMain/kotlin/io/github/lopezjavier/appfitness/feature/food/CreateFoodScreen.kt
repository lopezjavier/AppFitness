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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_ml
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
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_label_title
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_load_failed
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_name
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_offline_hint
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_protein
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_locked
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_ml
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.NewFoodError
import org.jetbrains.compose.resources.stringResource

private const val MAX_NAME_LENGTH = 200
private const val NUTRIENT_DECIMALS = 2
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
                        IdentityBlock(state, onEvent)
                        NutritionBlock(state, onEvent)
                        LabelBlock(state, onEvent)
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
            enabled = !state.isEdit,
        )
        if (state.isEdit) {
            Text(
                text = stringResource(Res.string.create_food_unit_locked),
                style = AppTheme.typography.bodySmall,
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

@Composable
private fun NutritionBlock(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    val kcalLabel = stringResource(
        if (state.baseUnit == "ml") Res.string.create_food_kcal_ml else Res.string.create_food_kcal_g,
    )
    val errors = state.errors
    val kcal = NutrientSpec(kcalLabel, null, "create_food_kcal_field")
    val protein =
        NutrientSpec(stringResource(Res.string.create_food_protein), Macro.Protein, "create_food_protein_field")
    val carbs = NutrientSpec(stringResource(Res.string.create_food_carbs), Macro.Carbs, "create_food_carbs_field")
    val fat = NutrientSpec(stringResource(Res.string.create_food_fat), Macro.Fat, "create_food_fat_field")
    fun change(field: CreateFoodField): (String) -> Unit = { onEvent(CreateFoodEvent.FieldChanged(field, it)) }
    FieldGrid(
        listOf(
            { NutrientField(kcal, state.kcal, errors.kcal, change(CreateFoodField.Kcal)) },
            { NutrientField(protein, state.protein, errors.protein, change(CreateFoodField.Protein)) },
            { NutrientField(carbs, state.carbs, errors.carbs, change(CreateFoodField.Carbs)) },
            { NutrientField(fat, state.fat, errors.fat, change(CreateFoodField.Fat)) },
        ),
    )
}

/** The usual label rows (US-25); the detailed groups follow as collapsible sections. */
@Composable
private fun LabelBlock(state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(Res.string.create_food_label_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("create_food_label_title"),
        )
        Text(
            text = stringResource(
                if (state.baseUnit == "ml") {
                    Res.string.add_food_nutrition_per_100_ml
                } else {
                    Res.string.add_food_nutrition_per_100_g
                },
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        KindGrid(CreateFoodSection.USUAL_KINDS, state, onEvent)
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
