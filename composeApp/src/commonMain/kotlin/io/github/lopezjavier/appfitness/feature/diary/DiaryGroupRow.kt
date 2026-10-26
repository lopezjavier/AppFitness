package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_collapsed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_section_expanded
import io.github.lopezjavier.appfitness.composeapp.resources.ic_expand_more
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.MealRow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A comida group of a meal (ADR-68): a header (name, kcal, macros, chevron; one merged button) and, while expanded,
 * its ingredients as [LoggedEntryRow]s (tap to edit, swipe to delete). Collapsed ingredients are not composed.
 */
@Composable
internal fun DiaryGroupCard(group: MealRow.Group, actions: EntryActions, modifier: Modifier = Modifier) {
    val expanded = group.groupId in actions.expandedGroupIds
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
                    text = group.name,
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.diary_meal_kcal, wholeHalfUp(group.totals.kcal)),
                    style = AppTheme.typography.labelNumeric,
                    color = AppTheme.colors.textPrimary,
                )
            }
            MacroSummary(group.totals)
        }
        Icon(
            painter = painterResource(Res.drawable.ic_expand_more),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(Spacing.xl).rotate(if (expanded) EXPANDED_CHEVRON_ROTATION else 0f),
        )
    }
}
