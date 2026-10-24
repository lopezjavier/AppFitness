package io.github.lopezjavier.appfitness.feature.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_nutrient_cd
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_nutrient_label
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_section_fats
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_section_filled
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_section_minerals
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_section_vitamins
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_word_g
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_word_mg
import io.github.lopezjavier.appfitness.composeapp.resources.create_food_unit_word_ug
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.micronutrients_expanded
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MicronutrientKind
import io.github.lopezjavier.appfitness.domain.model.MicronutrientUnit
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val LABEL_DECIMALS = 4
private const val CHEVRON_EXPANDED_ROTATION = 180f

/** A collapsible group of label nutrients: a 48 dp header with the filled count and a chevron. */
@Composable
internal fun SectionBlock(section: CreateFoodSection, state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    val expanded = section in state.expandedSections
    val filled = section.kinds.count { state.nutrients[it].orEmpty().isNotBlank() }
    val title = stringResource(
        when (section) {
            CreateFoodSection.Fats -> Res.string.create_food_section_fats
            CreateFoodSection.Vitamins -> Res.string.create_food_section_vitamins
            CreateFoodSection.Minerals -> Res.string.create_food_section_minerals
        },
    )
    val stateText = stringResource(
        if (expanded) Res.string.micronutrients_expanded else Res.string.micronutrients_collapsed,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        HorizontalDivider(color = AppTheme.colors.divider)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .clickable(role = Role.Button) { onEvent(CreateFoodEvent.ToggleSection(section)) }
                .semantics { stateDescription = stateText }
                .testTag("create_food_section_${section.id}_toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (filled > 0) {
                Text(
                    text = stringResource(Res.string.create_food_section_filled, filled),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("create_food_section_${section.id}_filled"),
                )
            }
            Icon(
                painter = painterResource(Res.drawable.ic_expand_more),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl).rotate(if (expanded) CHEVRON_EXPANDED_ROTATION else 0f),
            )
        }
        if (expanded) KindGrid(section.kinds, state, onEvent)
    }
}

@Composable
internal fun KindGrid(kinds: List<MicronutrientKind>, state: CreateFoodUiState, onEvent: (CreateFoodEvent) -> Unit) {
    FieldGrid(
        kinds.map { kind ->
            {
                val name = stringResource(micronutrientLabel(kind))
                val spec = NutrientSpec(
                    label = stringResource(
                        Res.string.create_food_nutrient_label,
                        name,
                        stringResource(micronutrientUnitLabel(kind.labelUnit)),
                    ),
                    macro = null,
                    tag = "create_food_nutrient_${micronutrientTag(kind)}",
                    spokenLabel = stringResource(
                        Res.string.create_food_nutrient_cd,
                        name,
                        stringResource(unitWord(kind.labelUnit)),
                    ),
                    decimals = LABEL_DECIMALS,
                )
                NutrientField(spec, state.nutrients[kind].orEmpty(), state.errors.nutrients[kind]) {
                    onEvent(CreateFoodEvent.NutrientChanged(kind, it))
                }
            }
        },
    )
}

private fun unitWord(unit: MicronutrientUnit) = when (unit) {
    MicronutrientUnit.G -> Res.string.create_food_unit_word_g
    MicronutrientUnit.MG -> Res.string.create_food_unit_word_mg
    MicronutrientUnit.UG -> Res.string.create_food_unit_word_ug
}
