package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
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
import io.github.lopezjavier.appfitness.composeapp.resources.add_item
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_custom
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_short
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_carbs
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_fat
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_protein
import io.github.lopezjavier.appfitness.composeapp.resources.diary_save_template_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.ic_save_meal
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CompactAddButton
import io.github.lopezjavier.appfitness.designsystem.CompactKcalPill
import io.github.lopezjavier.appfitness.designsystem.CompactTonalIconButton
import io.github.lopezjavier.appfitness.designsystem.Macro
import io.github.lopezjavier.appfitness.designsystem.MacroDefaults
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MealRow
import io.github.lopezjavier.appfitness.domain.model.MealSlot
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal const val EXPANDED_CHEVRON_ROTATION = 180f

/**
 * The object of the add button: "a <comida>" with the meal name as typed, no article (R-69: a meal named by the user
 * has no known gender), so the [AddItemButton] reads "Añadir a Cena".
 */
@Composable
internal fun addObjectLabel(slot: MealSlot): String = stringResource(Res.string.diary_add_object_custom, slot.label)

/**
 * One meal (R-75): a header with the toggle (name, chevron, kcal pill, macros) and two buttons, then the entries
 * while expanded. [onSaveTemplate] saves the entries as a comida and shows only while the meal has entries; [onAdd]
 * ("+Añadir") is always visible, collapsed or expanded.
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
    AppCard(
        modifier.fillMaxWidth().testTag("diary_section_${section.slot.id}"),
        // No padding above the name row (R-75).
        contentPadding = PaddingValues(
            start = Spacing.cardPadding,
            end = Spacing.cardPadding,
            bottom = Spacing.cardPadding,
        ),
    ) {
        SectionHeader(
            section = section,
            collapsed = collapsed,
            onToggle = onToggle,
            onAdd = onAdd,
            onSaveTemplate = onSaveTemplate.takeIf { section.entries.isNotEmpty() },
        )
        if (!collapsed) MealBody(section, actions)
    }
}

/** The entries of a meal; an empty meal shows nothing below its header (the add button is in the header). */
@Composable
private fun MealBody(section: MealSectionUi, actions: EntryActions) {
    if (section.rows.isEmpty()) return
    Column(Modifier.padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        section.rows.forEach { row ->
            when (row) {
                is MealRow.Single -> key(row.entry.id) { LoggedEntryRow(row.entry, actions) }
                is MealRow.Group -> key("group-${row.groupId}") { DiaryGroupCard(row, actions) }
            }
        }
    }
}

/**
 * The header: the toggle (name, chevron, kcal pill and macros, one merged button) takes the width; the save button
 * and "+Añadir", when set, sit outside it at the trailing edge, aligned with the name row, each its own 48 dp target.
 */
@Composable
private fun SectionHeader(
    section: MealSectionUi,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)?,
    onSaveTemplate: (() -> Unit)?,
) {
    val id = section.slot.id
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionToggle(
            section = section,
            collapsed = collapsed,
            onToggle = onToggle,
            modifier = Modifier.weight(1f).testTag("diary_section_${id}_toggle"),
        )
        if (onSaveTemplate != null) {
            CompactTonalIconButton(
                contentDescription = stringResource(Res.string.diary_save_template_cd),
                icon = Res.drawable.ic_save_meal,
                testTag = "diary_section_${id}_save_template",
                onClick = onSaveTemplate,
            )
        }
        if (onAdd != null) {
            CompactAddButton(
                text = stringResource(Res.string.diary_add_short),
                contentDescription = stringResource(Res.string.add_item, addObjectLabel(section.slot)),
                onClick = onAdd,
                modifier = Modifier.testTag("diary_section_${id}_add"),
            )
        }
    }
}

/** The merged toggle of a meal header: name and chevron, the kcal pill under the name, then the macros line. */
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
    Column(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                stateDescription = state
                role = Role.Button
            },
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.heightIn(min = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = section.slot.label,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painter = painterResource(Res.drawable.ic_expand_more),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl).rotate(if (collapsed) 0f else EXPANDED_CHEVRON_ROTATION),
            )
            if (section.entries.isNotEmpty()) {
                CompactKcalPill(stringResource(Res.string.diary_meal_kcal, wholeHalfUp(section.totals.kcal)))
            }
        }
        MacroSummary(section.totals)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MacroSummary(totals: MealTotals) {
    // Whole items wrap ("P 15 g" never splits), so the line never overlaps the header buttons.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        MealSummaryText(stringResource(Res.string.diary_meal_protein, wholeHalfUp(totals.proteinG)), Macro.Protein)
        MealSummaryText(stringResource(Res.string.diary_meal_carbs, wholeHalfUp(totals.carbsG)), Macro.Carbs)
        MealSummaryText(stringResource(Res.string.diary_meal_fat, wholeHalfUp(totals.fatG)), Macro.Fat)
    }
}
