package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_entry_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EntryTotals
import io.github.lopezjavier.appfitness.designsystem.FoodEntryRowModel
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SwipeToDeleteRow
import io.github.lopezjavier.appfitness.domain.model.MealRow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A comida group of a meal (ADR-68): a header (name, kcal, macros, chevron; one merged button) and, while expanded,
 * its ingredients as [LoggedEntryRow]s (tap to edit, swipe to delete). Collapsed ingredients are not composed.
 * Collapsed, the whole group is one swipe-to-delete row that removes the food (R-71).
 */
@Composable
internal fun DiaryGroupCard(group: MealRow.Group, actions: EntryActions, modifier: Modifier = Modifier) {
    val expanded = group.groupId in actions.expandedGroupIds
    // The box starts 8 dp from the card edge so its text lines up with the plain rows (C-60).
    val bled = modifier.bleedHorizontally(Spacing.sm)
    // R-71: collapsed, the whole food swipes away; expanded, only each ingredient does (the card is not swipeable).
    if (expanded || !actions.canDelete) {
        GroupCardBody(group, actions, expanded, bled)
    } else {
        SwipeToDeleteRow(
            deleteLabel = stringResource(Res.string.diary_entry_delete_cd, group.name),
            enabled = true,
            onDelete = { actions.onDeleteGroup(group.groupId) },
            backgroundTag = "diary_group_${group.groupId}_delete",
            modifier = bled.clip(AppShapes.base),
            containerColor = AppTheme.colors.surfaceRaised,
        ) { GroupCardBody(group, actions, expanded = false) }
    }
}

@Composable
private fun GroupCardBody(
    group: MealRow.Group,
    actions: EntryActions,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.base)
            .background(AppTheme.colors.surfaceOverlay)
            .padding(Spacing.sm)
            .testTag("diary_group_${group.groupId}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GroupHeader(
            group = group,
            expanded = expanded,
            onToggle = { actions.onToggleGroup(group.groupId) },
            modifier = Modifier.testTag("diary_group_${group.groupId}_toggle"),
        )
        if (expanded) {
            Column(Modifier.padding(start = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                group.entries.forEach {
                    key(it.id) { LoggedEntryRow(it, actions) }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: MealRow.Group, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val state = stringResource(if (expanded) Res.string.diary_section_expanded else Res.string.diary_section_collapsed)
    // C-60: the same slots as FoodEntryRow (name + chevron as headline, kcal over the macros as trailing).
    val model = FoodEntryRowModel(
        id = group.groupId,
        name = group.name,
        detail = "",
        kcal = wholeHalfUpInt(group.totals.kcal),
        proteinG = wholeHalfUpInt(group.totals.proteinG),
        carbsG = wholeHalfUpInt(group.totals.carbsG),
        fatG = wholeHalfUpInt(group.totals.fatG),
    )
    AppListRow(
        headline = group.name,
        modifier = modifier.semantics(mergeDescendants = true) { stateDescription = state },
        onClick = onToggle,
        headlineTrailing = {
            Icon(
                painter = painterResource(Res.drawable.ic_expand_more),
                contentDescription = null,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl).rotate(if (expanded) EXPANDED_CHEVRON_ROTATION else 0f),
            )
        },
        trailing = { EntryTotals(model) },
    )
}

/** Lets a row draw [amount] beyond each horizontal edge of its parent's content box (C-60). */
private fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    val width = constraints.maxWidth + 2 * extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
