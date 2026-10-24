package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_ml
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_fat
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_protein
import io.github.lopezjavier.appfitness.composeapp.resources.label_nutrients_energy
import io.github.lopezjavier.appfitness.composeapp.resources.label_nutrients_energy_value
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_show_all
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_show_less
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_value
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppColors
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.LabelNutrient
import io.github.lopezjavier.appfitness.domain.model.LabelNutrientKey
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import org.jetbrains.compose.resources.stringResource

private val RowMinHeight = Spacing.xl + Spacing.lg
private val NESTED_KINDS = setOf(
    MicronutrientKind.SaturatedFat,
    MicronutrientKind.MonounsaturatedFat,
    MicronutrientKind.PolyunsaturatedFat,
    MicronutrientKind.Sugars,
    MicronutrientKind.Polyols,
    MicronutrientKind.Starch,
    MicronutrientKind.Fiber,
)

/**
 * The nutrients of a food per 100 [baseUnit] in label order (US-47), one card for manual and Open Food Facts foods.
 * The rows of the usual label are always visible; the rest sits behind a toggle whose state the caller owns
 * ([expanded], saved across recreation).
 */
@Composable
internal fun LabelNutrientsCard(
    rows: List<LabelNutrient>,
    baseUnit: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsible = rows.any { !it.inUsualLabel }
    val visible = if (collapsible && !expanded) rows.filter { it.inUsualLabel } else rows
    val sizeModifier = if (LocalAnimationsEnabled.current) Modifier.animateContentSize() else Modifier
    AppCard(modifier.fillMaxWidth().then(sizeModifier).testTag("add_food_label_nutrients")) {
        Text(
            text = stringResource(
                if (baseUnit ==
                    "ml"
                ) {
                    Res.string.add_food_nutrition_per_100_ml
                } else {
                    Res.string.add_food_nutrition_per_100_g
                },
            ),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = Spacing.sm).testTag("add_food_label_basis"),
        )
        visible.forEach { LabelRow(it) }
        if (collapsible) {
            val stateText = stringResource(
                if (expanded) Res.string.micronutrients_expanded else Res.string.micronutrients_collapsed,
            )
            GhostButton(
                text = if (expanded) {
                    stringResource(Res.string.micronutrients_show_less)
                } else {
                    stringResource(Res.string.micronutrients_show_all, rows.size)
                },
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = stateText }
                    .testTag("add_food_label_toggle"),
            )
        }
    }
}

/** One nutrient: name on the left (wraps up to two lines), value on the right (never wraps). */
@Composable
private fun LabelRow(row: LabelNutrient) {
    val key = row.key
    val name = stringResource(labelName(key))
    val amount = formatAmount(row.amount)
    val value = row.unit?.let {
        stringResource(Res.string.micronutrients_value, amount, stringResource(micronutrientUnitLabel(it)))
    } ?: stringResource(Res.string.label_nutrients_energy_value, amount)
    val nested = key is LabelNutrientKey.Extra && key.kind in NESTED_KINDS
    val colors = AppTheme.colors
    val valueColor = valueColor(key, colors)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(start = if (nested) Spacing.lg else Spacing.none)
            .semantics(mergeDescendants = true) {}
            .testTag("add_food_label_row_${rowTag(key)}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = AppTheme.typography.bodyLarge,
            color = if (nested) colors.textSecondary else colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = Spacing.md),
        )
        Text(
            text = value,
            style = AppTheme.typography.labelNumeric,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun labelName(key: LabelNutrientKey) = when (key) {
    LabelNutrientKey.Energy -> Res.string.label_nutrients_energy
    LabelNutrientKey.Fat -> Res.string.create_food_fat
    LabelNutrientKey.Carbs -> Res.string.create_food_carbs
    LabelNutrientKey.Protein -> Res.string.create_food_protein
    is LabelNutrientKey.Extra -> micronutrientLabel(key.kind)
}

/** Macro rows tint only their value with the fixed macro colours. */
private fun valueColor(key: LabelNutrientKey, colors: AppColors): Color = when (key) {
    LabelNutrientKey.Fat -> MacroDefaults.color(Macro.Fat, colors)
    LabelNutrientKey.Carbs -> MacroDefaults.color(Macro.Carbs, colors)
    LabelNutrientKey.Protein -> MacroDefaults.color(Macro.Protein, colors)
    else -> colors.textPrimary
}

private fun rowTag(key: LabelNutrientKey): String = when (key) {
    LabelNutrientKey.Energy -> "energy"
    LabelNutrientKey.Fat -> "fat"
    LabelNutrientKey.Carbs -> "carbs"
    LabelNutrientKey.Protein -> "protein"
    is LabelNutrientKey.Extra -> micronutrientTag(key.kind)
}
