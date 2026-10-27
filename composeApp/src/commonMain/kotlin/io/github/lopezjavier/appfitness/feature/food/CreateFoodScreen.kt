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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
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
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_protein
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_salt
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_saturated_of
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_sugars_of
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_title
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_locked
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_ml
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_refused
import io.github.lopezjavier.appfitness.composeapp.resources.edit_food_title
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberInput
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
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
 * Create / edit food form (US-18, US-22, US-47) on [AppEditorScreen] (R-10..R-14, FRM-2): name, brand, base unit,
 * nutrition per 100, the label nutrients and named portions, one label style and "Siguiente" chaining from field to
 * field. "Guardar" is pinned in the footer; back asks "¿Descartar los cambios?" once something was edited. In edit mode
 * the form shows a skeleton while loading and a retry when the food could not be read. [onBack] leaves the form (also
 * after "Descartar"); a failed save is reported by the caller through the app messages (R-28).
 */
@Composable
fun CreateFoodScreen(
    state: CreateFoodUiState,
    onEvent: (CreateFoodEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEditorScreen(
        title = stringResource(if (state.isEdit) Res.string.edit_food_title else Res.string.create_food_title),
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        isOnline = !state.isOffline,
        onSave = { onEvent(CreateFoodEvent.Save) },
        onClose = onBack,
        modifier = modifier.testTag("create_food_screen"),
        // A new food (also one prefilled by a photo estimate) saves as soon as it is valid; an edit needs a change.
        saveEnabledWhenClean = !state.isEdit,
        canSave = state.canSave || state.isOffline,
        testTagPrefix = "create_food",
    ) {
        when {
            state.loadFailed -> ErrorState(
                message = stringResource(Res.string.create_food_load_failed),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(CreateFoodEvent.Retry) },
                modifier = Modifier.padding(Spacing.screenMargin),
                testTagPrefix = "create_food_retry",
            )

            state.isLoading -> FormLoading()

            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
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
            imeAction = ImeAction.Next,
        )
        AppTextField(
            value = state.brand,
            onValueChange = { onEvent(CreateFoodEvent.FieldChanged(CreateFoodField.Brand, it)) },
            label = stringResource(Res.string.create_food_brand),
            maxLength = MAX_NAME_LENGTH,
            errorText = state.errors.brand?.let { errorMessage(it) },
            testTagPrefix = "create_food_brand_field",
            imeAction = ImeAction.Next,
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
    fun KindField(
        kind: MicronutrientKind,
        label: String,
        macro: Macro?,
        indent: Boolean = false,
        imeAction: ImeAction = ImeAction.Next,
    ) {
        val spec =
            NutrientSpec(label, macro, "create_food_nutrient_${micronutrientTag(kind)}", decimals = LABEL_DECIMALS)
        Box(if (indent) Modifier.padding(start = Spacing.lg) else Modifier) {
            NutrientField(spec, state.nutrients[kind].orEmpty(), errors.nutrients[kind], imeAction) {
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
            ImeAction.Next,
            change(CreateFoodField.Kcal),
        )
        val fat = NutrientSpec(stringResource(Res.string.create_food_fat_total), Macro.Fat, "create_food_fat_field")
        NutrientField(fat, state.fat, errors.fat, ImeAction.Next, change(CreateFoodField.Fat))
        KindField(MicronutrientKind.SaturatedFat, stringResource(Res.string.create_food_saturated_of), Macro.Fat, true)
        val carbs =
            NutrientSpec(stringResource(Res.string.create_food_carbs_total), Macro.Carbs, "create_food_carbs_field")
        NutrientField(carbs, state.carbs, errors.carbs, ImeAction.Next, change(CreateFoodField.Carbs))
        KindField(MicronutrientKind.Sugars, stringResource(Res.string.create_food_sugars_of), Macro.Carbs, true)
        KindField(MicronutrientKind.Fiber, stringResource(Res.string.create_food_fiber), null)
        val protein =
            NutrientSpec(stringResource(Res.string.create_food_protein), Macro.Protein, "create_food_protein_field")
        NutrientField(protein, state.protein, errors.protein, ImeAction.Next, change(CreateFoodField.Protein))
        KindField(MicronutrientKind.Salt, stringResource(Res.string.create_food_salt), null, imeAction = ImeAction.Done)
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
internal fun NutrientField(
    spec: NutrientSpec,
    value: String,
    error: NewFoodError?,
    imeAction: ImeAction = ImeAction.Done,
    onChange: (String) -> Unit,
) {
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
            imeAction = imeAction,
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
private fun errorMessage(error: NewFoodError): String = stringResource(
    when (error) {
        NewFoodError.Required -> Res.string.create_food_error_required
        NewFoodError.TooLong -> Res.string.create_food_error_too_long
        NewFoodError.Negative -> Res.string.create_food_error_negative
        NewFoodError.TooLarge -> Res.string.create_food_error_too_large
        NewFoodError.NotANumber -> Res.string.create_food_error_not_a_number
    },
)
