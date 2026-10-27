@file:Suppress("MatchingDeclarationName")

package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.meals_action_down
import io.github.lopezjavier.appfitness.composeapp.resources.meals_action_up
import io.github.lopezjavier.appfitness.composeapp.resources.meals_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_hint_has_entries
import io.github.lopezjavier.appfitness.composeapp.resources.meals_last_meal_hint
import io.github.lopezjavier.appfitness.composeapp.resources.meals_reorder_cd
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

private const val LABEL_MAX_LINES = 2

/** What a row needs besides its own state: whether the editor is busy. */
internal data class MealRowContext(val interactive: Boolean)

/**
 * One configuration row (R-20): drag handle, name (tap opens the rename Name sheet) and the "Quitar" trash button,
 * which only stages the removal (R-11); it is disabled, with the reason in the row, for a meal that has logged entries
 * and for the last meal. A divider closes the row. Subir and Bajar are custom accessibility actions of the row.
 */
@Composable
internal fun ReorderableItemScope.MealSlotRow(
    row: MealSlotRowUi,
    index: Int,
    context: MealRowContext,
    onEvent: (MealSlotsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val upLabel = stringResource(Res.string.meals_action_up)
    val downLabel = stringResource(Res.string.meals_action_down)
    Column(
        modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceBase)
            .testTag("meals_row_${row.key}")
            .semantics { customActions = moveActions(row, index, upLabel, downLabel, onEvent) },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DragHandle(
                contentDescription = stringResource(Res.string.meals_reorder_cd, row.label),
                modifier = Modifier.dragHandle().testTag("meals_handle_${row.key}"),
            )
            Text(
                text = row.label,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = LABEL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = context.interactive) { onEvent(MealSlotsEvent.Rename(row.key)) }
                    .padding(horizontal = Spacing.xs)
                    .testTag("meals_name_${row.key}"),
            )
            AppIconButton(
                contentDescription = stringResource(Res.string.meals_delete_cd, row.label),
                icon = Res.drawable.ic_delete,
                testTag = "meals_delete_${row.key}",
                onClick = { onEvent(MealSlotsEvent.Delete(row.key)) },
                enabled = context.interactive && row.canDelete,
            )
        }
        if (row.inUse || row.isLastMeal) {
            Text(
                text = stringResource(
                    if (row.inUse) Res.string.meals_hint_has_entries else Res.string.meals_last_meal_hint,
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.minTouchTarget + Spacing.xs, end = Spacing.xs, bottom = Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("meals_hint_${row.key}"),
            )
        }
        Box(Modifier.fillMaxWidth().height(AppBorder.thin).background(AppTheme.colors.divider))
    }
}

/** Subir / Bajar as accessibility actions of the row, only the ones that make sense for it. */
private fun moveActions(
    row: MealSlotRowUi,
    index: Int,
    upLabel: String,
    downLabel: String,
    onEvent: (MealSlotsEvent) -> Unit,
): List<CustomAccessibilityAction> = listOfNotNull(
    CustomAccessibilityAction(upLabel) {
        onEvent(MealSlotsEvent.MoveUp(index))
        true
    }.takeIf { row.canMoveUp },
    CustomAccessibilityAction(downLabel) {
        onEvent(MealSlotsEvent.MoveDown(index))
        true
    }.takeIf { row.canMoveDown },
)
