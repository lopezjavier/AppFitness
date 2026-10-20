package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_breakfast
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_custom
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_dinner
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_lunch
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_snack
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_breakfast
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_dinner
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_lunch
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_snack
import io.github.lopezjavier.appfitness.composeapp.resources.diary_remaining_hint
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val COLLAPSED_CHEVRON_ROTATION = 180f

/** Default label and add-button wording of the four default meals. */
private fun defaultLabel(code: MealCode): StringResource = when (code) {
    MealCode.Breakfast -> Res.string.diary_meal_breakfast
    MealCode.Lunch -> Res.string.diary_meal_lunch
    MealCode.Snack -> Res.string.diary_meal_snack
    MealCode.Dinner -> Res.string.diary_meal_dinner
}

private fun addLabel(code: MealCode): StringResource = when (code) {
    MealCode.Breakfast -> Res.string.diary_add_breakfast
    MealCode.Lunch -> Res.string.diary_add_lunch
    MealCode.Snack -> Res.string.diary_add_snack
    MealCode.Dinner -> Res.string.diary_add_dinner
}

/** "Añadir a la cena" for a default meal; "Añadir a <label>" for a custom or renamed one, so it never disagrees. */
@Composable
private fun addButtonText(slot: MealSlot): String {
    val code = slot.code
    return if (code != null && slot.label == stringResource(defaultLabel(code))) {
        stringResource(addLabel(code))
    } else {
        stringResource(Res.string.diary_add_custom, slot.label)
    }
}

/** One meal: a tappable header with its totals, the entries (or the remaining-kcal hint) and the add button. */
@Composable
internal fun MealSectionCard(
    section: MealSectionUi,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = section.slot.id
    AppCard(modifier.fillMaxWidth().testTag("diary_section_$id")) {
        SectionHeader(section, collapsed, onToggle, Modifier.testTag("diary_section_${id}_toggle"))
        if (!collapsed) {
            Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                section.entries.forEach { DiaryEntryRow(it) }
                if (section.showRemainingHint && section.remainingKcal != null) {
                    Text(
                        text = stringResource(
                            Res.string.diary_remaining_hint,
                            AppNumberFormat.integer(section.remainingKcal),
                        ),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("diary_section_${id}_empty_hint"),
                    )
                }
                SecondaryButton(
                    text = addButtonText(section.slot),
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().testTag("diary_section_${id}_add"),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    section: MealSectionUi,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(
        if (collapsed) Res.string.diary_section_collapsed else Res.string.diary_section_expanded,
    )
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                stateDescription = state
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = section.slot.label,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.diary_meal_kcal, wholeHalfUp(section.totals.kcal)),
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.textPrimary,
                )
            }
            MacroSummary(section.totals)
        }
        Icon(
            painter = painterResource(Res.drawable.ic_expand_more),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl).rotate(if (collapsed) 0f else COLLAPSED_CHEVRON_ROTATION),
        )
    }
}

@Composable
private fun MealSummaryText(text: String, macro: Macro) {
    Text(
        text = text,
        style = AppTheme.typography.bodySmall,
        color = MacroDefaults.color(macro, AppTheme.colors),
    )
}

@Composable
private fun MacroSummary(totals: MealTotals) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MealSummaryText(stringResource(Res.string.diary_meal_protein, wholeHalfUp(totals.proteinG)), Macro.Protein)
        MealSummaryText(stringResource(Res.string.diary_meal_carbs, wholeHalfUp(totals.carbsG)), Macro.Carbs)
        MealSummaryText(stringResource(Res.string.diary_meal_fat, wholeHalfUp(totals.fatG)), Macro.Fat)
    }
}
