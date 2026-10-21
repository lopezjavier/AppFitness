package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_g
import io.github.lopezjavier.appfitness.composeapp.resources.add_food_nutrition_per_100_ml
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_show_all
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_show_less
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_title
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_value
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.LocalAnimationsEnabled
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.Micronutrient
import io.github.lopezjavier.appfitness.domain.model.Micronutrients
import org.jetbrains.compose.resources.stringResource

/** Rows shown before the "Ver todos" toggle; the quantity field and "Añadir" stay reachable. */
internal const val COLLAPSED_MICRONUTRIENT_ROWS = 6

/**
 * The extra nutrients of a food per 100 [baseUnit] (US-25): neutral text only, no targets. Collapsed to the first
 * [COLLAPSED_MICRONUTRIENT_ROWS] rows behind a toggle; the caller owns [expanded] (saved across recreation).
 */
@Composable
internal fun MicronutrientsCard(
    micronutrients: Micronutrients,
    baseUnit: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = micronutrients.items
    val collapsible = items.size > COLLAPSED_MICRONUTRIENT_ROWS
    val visible = if (collapsible && !expanded) items.take(COLLAPSED_MICRONUTRIENT_ROWS) else items
    val sizeModifier = if (LocalAnimationsEnabled.current) Modifier.animateContentSize() else Modifier
    AppCard(modifier.fillMaxWidth().then(sizeModifier).testTag("add_food_micronutrients")) {
        Text(
            text = stringResource(Res.string.micronutrients_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("add_food_micronutrients_title"),
        )
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
            modifier = Modifier.padding(bottom = Spacing.sm).testTag("add_food_micronutrients_basis"),
        )
        visible.forEach { MicronutrientRow(it) }
        if (collapsible) {
            val stateText = stringResource(
                if (expanded) Res.string.micronutrients_expanded else Res.string.micronutrients_collapsed,
            )
            GhostButton(
                text = if (expanded) {
                    stringResource(Res.string.micronutrients_show_less)
                } else {
                    stringResource(Res.string.micronutrients_show_all, items.size)
                },
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = stateText }
                    .testTag("add_food_micronutrients_toggle"),
            )
        }
    }
}

/** One nutrient: label on the left (wraps up to two lines), value on the right (never wraps). */
@Composable
private fun MicronutrientRow(item: Micronutrient) {
    val value = stringResource(
        Res.string.micronutrients_value,
        formatAmount(item.amount),
        stringResource(micronutrientUnitLabel(item.unit)),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .semantics(mergeDescendants = true) {}
            .testTag("add_food_micronutrient_${micronutrientTag(item.kind)}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(micronutrientLabel(item.kind)),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = Spacing.md),
        )
        Text(
            text = value,
            style = AppTheme.typography.labelNumeric,
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}
