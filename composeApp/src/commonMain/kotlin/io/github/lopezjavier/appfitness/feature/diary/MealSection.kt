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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_breakfast
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_custom
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_dinner
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_lunch
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_snack
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_breakfast
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_dinner
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_lunch
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_snack
import io.github.lopezjavier.appfitness.composeapp.resources.diary_remaining_hint
import io.github.lopezjavier.appfitness.composeapp.resources.diary_save_template_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.ic_save_meal
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MealCode
import io.github.lopezjavier.appfitness.domain.model.MealRow
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal const val EXPANDED_CHEVRON_ROTATION = 180f

/** Default label of the four default meals. */
private fun defaultLabel(code: MealCode): StringResource = when (code) {
    MealCode.Breakfast -> Res.string.diary_meal_breakfast
    MealCode.Lunch -> Res.string.diary_meal_lunch
    MealCode.Snack -> Res.string.diary_meal_snack
    MealCode.Dinner -> Res.string.diary_meal_dinner
}

private fun addObject(code: MealCode): StringResource = when (code) {
    MealCode.Breakfast -> Res.string.diary_add_object_breakfast
    MealCode.Lunch -> Res.string.diary_add_object_lunch
    MealCode.Snack -> Res.string.diary_add_object_snack
    MealCode.Dinner -> Res.string.diary_add_object_dinner
}

/**
 * The object of the add button ("a la cena" for a default meal, "a <label>" for a custom or renamed one), so the
 * [AddItemButton] reads "Añadir a la cena" and never disagrees with the meal's name.
 */
@Composable
private fun addObjectLabel(slot: MealSlot): String {
    val code = slot.code
    return if (code != null && slot.label == stringResource(defaultLabel(code))) {
        stringResource(addObject(code))
    } else {
        stringResource(Res.string.diary_add_object_custom, slot.label)
    }
}

/**
 * One meal: a header with its totals (tap to collapse), the entries (or the remaining-kcal hint) and the add button.
 * [onSaveTemplate] saves the entries of the meal as a comida; its icon shows only while the meal has entries.
 */
@Suppress("LongParameterList") // One callback per action of the meal: toggle, add and save, plus the entry actions.
@Composable
internal fun MealSectionCard(
    section: MealSectionUi,
    collapsed: Boolean,
    actions: EntryActions,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onSaveTemplate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth().testTag("diary_section_${section.slot.id}")) {
        SectionHeader(
            section = section,
            collapsed = collapsed,
            onToggle = onToggle,
            onSaveTemplate = onSaveTemplate.takeIf { section.entries.isNotEmpty() },
        )
        if (!collapsed) MealBody(section, actions, onAdd)
    }
}

/** The entries of a meal (or its remaining-kcal hint) and the add button. */
@Composable
private fun MealBody(section: MealSectionUi, actions: EntryActions, onAdd: () -> Unit) {
    val id = section.slot.id
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        section.rows.forEach { row ->
            when (row) {
                is MealRow.Single -> key(row.entry.id) { LoggedEntryRow(row.entry, actions) }
                is MealRow.Group -> key("group-${row.groupId}") { DiaryGroupCard(row, actions) }
            }
        }
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
        AddItemButton(
            objectLabel = addObjectLabel(section.slot),
            onClick = onAdd,
            modifier = Modifier.testTag("diary_section_${id}_add"),
        )
    }
}

/**
 * The header row: the toggle (name, totals and chevron, one merged button) takes the width; the save icon, when
 * [onSaveTemplate] is set, sits outside it so each is its own 48 dp target.
 */
@Composable
private fun SectionHeader(
    section: MealSectionUi,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onSaveTemplate: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionToggle(
            section = section,
            collapsed = collapsed,
            onToggle = onToggle,
            modifier = Modifier.weight(1f).testTag("diary_section_${section.slot.id}_toggle"),
        )
        if (onSaveTemplate != null) {
            AppIconButton(
                contentDescription = stringResource(Res.string.diary_save_template_cd),
                icon = Res.drawable.ic_save_meal,
                testTag = "diary_section_${section.slot.id}_save_template",
                onClick = onSaveTemplate,
                tint = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** The merged toggle button of a meal header: name, totals and the chevron. */
@Composable
private fun SectionToggle(
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.size(Spacing.xl).rotate(if (collapsed) 0f else EXPANDED_CHEVRON_ROTATION),
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
internal fun MacroSummary(totals: MealTotals) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MealSummaryText(stringResource(Res.string.diary_meal_protein, wholeHalfUp(totals.proteinG)), Macro.Protein)
        MealSummaryText(stringResource(Res.string.diary_meal_carbs, wholeHalfUp(totals.carbsG)), Macro.Carbs)
        MealSummaryText(stringResource(Res.string.diary_meal_fat, wholeHalfUp(totals.fatG)), Macro.Fat)
    }
}
