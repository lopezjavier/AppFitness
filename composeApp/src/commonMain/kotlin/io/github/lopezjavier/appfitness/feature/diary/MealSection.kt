package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.add_item
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_object_custom
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_short
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_save_template_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.composeapp.resources.ic_save_meal
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.CompactAddButton
import io.github.lopezjavier.appfitness.designsystem.CompactKcalPill
import io.github.lopezjavier.appfitness.designsystem.CompactTonalIconButton
import io.github.lopezjavier.appfitness.designsystem.MacroLine
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.semiBold
import io.github.lopezjavier.appfitness.domain.model.MealRow
import io.github.lopezjavier.appfitness.domain.model.MealSlot
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
        // No padding above the name row (R-75); a collapsed or empty card keeps only 4 dp below (C-61).
        contentPadding = PaddingValues(
            start = Spacing.cardPadding,
            end = Spacing.cardPadding,
            bottom = if (collapsed || section.rows.isEmpty()) Spacing.xs else Spacing.cardPadding,
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
    // C-64: a 1 dp hairline centred in the 8 dp gap under the header.
    Box(Modifier.fillMaxWidth().height(Spacing.sm), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(AppBorder.thin).background(AppTheme.colors.borderSubtle))
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        section.rows.forEach { row ->
            when (row) {
                is MealRow.Single -> key(row.entry.id) { LoggedEntryRow(row.entry, actions) }
                is MealRow.Group -> key("group-${row.groupId}") { DiaryGroupCard(row, actions) }
            }
        }
    }
}

/**
 * The header: the toggle (name, chevron, kcal pill and macros, one merged button) takes the width left by the save
 * button and "+Añadir", which sit outside it at the trailing edge, each its own 48 dp target. The buttons are measured
 * first; when name + chevron + pill do not fit on one row beside them (360 dp, large font scale) the pill moves to
 * the macros line (R-78) and the buttons stay beside the name.
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
    val measurer = rememberTextMeasurer()
    val nameStyle = AppTheme.typography.titleMedium
    val pillStyle = AppTheme.typography.bodyMedium.semiBold()
    val pillText = if (section.entries.isEmpty()) {
        null
    } else {
        stringResource(
            Res.string.diary_meal_kcal,
            wholeHalfUp(section.totals.kcal),
        )
    }
    val saveDescription = stringResource(Res.string.diary_save_template_cd)
    val addText = stringResource(Res.string.diary_add_short)
    val addDescription = stringResource(Res.string.add_item, addObjectLabel(section.slot))
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val buttons = subcompose("buttons") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSaveTemplate != null) {
                    CompactTonalIconButton(
                        contentDescription = saveDescription,
                        icon = Res.drawable.ic_save_meal,
                        testTag = "diary_section_${id}_save_template",
                        onClick = onSaveTemplate,
                    )
                }
                if (onAdd != null) {
                    CompactAddButton(
                        text = addText,
                        contentDescription = addDescription,
                        onClick = onAdd,
                        modifier = Modifier.testTag("diary_section_${id}_add"),
                    )
                }
            }
        }.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
        val buttonsWidth = buttons.maxOfOrNull { it.width } ?: 0
        val toggleWidth = constraints.maxWidth - buttonsWidth
        val nameWidth = measurer.measure(section.slot.label, nameStyle, softWrap = false, maxLines = 1).size.width
        val pillWidth = pillText?.let {
            measurer.measure(it, pillStyle, softWrap = false, maxLines = 1).size.width + 2 * Spacing.md.roundToPx()
        } ?: 0
        val nameRowWidth =
            nameWidth + Spacing.xl.roundToPx() + if (pillText == null) 0 else 2 * Spacing.xs.roundToPx() + pillWidth
        val pillInline = nameRowWidth <= toggleWidth
        val toggle = subcompose("toggle") {
            SectionToggle(
                section = section,
                collapsed = collapsed,
                pillText = pillText,
                pillInline = pillInline,
                onToggle = onToggle,
                modifier = Modifier.testTag("diary_section_${id}_toggle"),
            )
        }.map { it.measure(Constraints(minWidth = toggleWidth, maxWidth = toggleWidth)) }
        val toggleHeight = toggle.maxOfOrNull { it.height } ?: 0
        val buttonsHeight = buttons.maxOfOrNull { it.height } ?: 0
        val height = maxOf(toggleHeight, buttonsHeight)
        layout(constraints.maxWidth, height) {
            toggle.forEach { it.place(0, (height - it.height) / 2) }
            buttons.forEach { it.place(toggleWidth, (height - it.height) / 2) }
        }
    }
}

/**
 * The merged toggle of a meal header: name and chevron (and the kcal pill when [pillInline]), then the macros line
 * 4 dp below (C-63), preceded by the pill when it did not fit beside the name.
 */
@Composable
private fun SectionToggle(
    section: MealSectionUi,
    collapsed: Boolean,
    pillText: String?,
    pillInline: Boolean,
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
            if (pillText != null && pillInline) CompactKcalPill(pillText)
        }
        val totals = section.totals
        val macros: @Composable (Modifier) -> Unit = { macroModifier ->
            MacroLine(
                proteinG = wholeHalfUp(totals.proteinG),
                carbsG = wholeHalfUp(totals.carbsG),
                fatG = wholeHalfUp(totals.fatG),
                modifier = macroModifier,
                muted = section.entries.isEmpty(),
            )
        }
        if (pillText != null && !pillInline) {
            Row(
                Modifier.padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CompactKcalPill(pillText)
                macros(Modifier.weight(1f))
            }
        } else {
            macros(Modifier.padding(top = Spacing.xs))
        }
    }
}
