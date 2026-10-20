@file:Suppress("MatchingDeclarationName")

package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_downward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_upward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.meals_action_down
import io.github.lopezjavier.appfitness.composeapp.resources.meals_action_up
import io.github.lopezjavier.appfitness.composeapp.resources.meals_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_error_blank
import io.github.lopezjavier.appfitness.composeapp.resources.meals_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.meals_hint_has_entries
import io.github.lopezjavier.appfitness.composeapp.resources.meals_label_field
import io.github.lopezjavier.appfitness.composeapp.resources.meals_move_down_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_move_up_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_reorder_cd
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError
import org.jetbrains.compose.resources.stringResource

private const val LABEL_MAX_LINES = 2

/** What a row needs besides its own state: whether the editor is busy. */
internal data class MealRowContext(val interactive: Boolean)

/**
 * One compact meal row: drag handle, name, then the icon buttons (rename, up, down, delete) on one line; a divider
 * closes it. While the name is being renamed the field replaces the name and the buttons. Subir and Bajar are also
 * custom accessibility actions of the row.
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
            if (row.isEditing) {
                RenameField(row, onEvent, Modifier.weight(1f).padding(vertical = Spacing.xs))
            } else {
                Text(
                    text = row.label,
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                    maxLines = LABEL_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs).testTag("meals_name_${row.key}"),
                )
                MealControls(row, index, context, onEvent)
            }
        }
        if (row.deleteBlocked) {
            Text(
                text = stringResource(Res.string.meals_hint_has_entries),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.error,
                modifier = Modifier
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("meals_hint_${row.key}"),
            )
        }
        Box(Modifier.fillMaxWidth().height(AppBorder.thin).background(AppTheme.colors.divider))
    }
}

/** The inline name field: it takes focus when it appears and Done confirms. */
@Composable
private fun RenameField(row: MealSlotRowUi, onEvent: (MealSlotsEvent) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AppTextField(
        value = row.label,
        onValueChange = { onEvent(MealSlotsEvent.SetLabel(row.key, it)) },
        label = stringResource(Res.string.meals_label_field),
        modifier = modifier.focusRequester(focus).focusGroup(),
        errorText = row.error?.let { errorText(it) },
        onDone = { onEvent(MealSlotsEvent.StopEdit) },
        testTagPrefix = "meals_label_${row.key}",
    )
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

/** Rename, up, down and delete; the delete button is set apart so it is not hit by mistake. */
@Composable
private fun MealControls(row: MealSlotRowUi, index: Int, context: MealRowContext, onEvent: (MealSlotsEvent) -> Unit) {
    AppIconButton(
        contentDescription = stringResource(Res.string.meals_edit_cd, row.label),
        icon = Res.drawable.ic_edit,
        testTag = "meals_edit_${row.key}",
        onClick = { onEvent(MealSlotsEvent.Edit(row.key)) },
        enabled = context.interactive,
    )
    AppIconButton(
        contentDescription = stringResource(Res.string.meals_move_up_cd, row.label),
        icon = Res.drawable.ic_arrow_upward,
        testTag = "meals_up_${row.key}",
        onClick = { onEvent(MealSlotsEvent.MoveUp(index)) },
        enabled = context.interactive && row.canMoveUp,
    )
    AppIconButton(
        contentDescription = stringResource(Res.string.meals_move_down_cd, row.label),
        icon = Res.drawable.ic_arrow_downward,
        testTag = "meals_down_${row.key}",
        onClick = { onEvent(MealSlotsEvent.MoveDown(index)) },
        enabled = context.interactive && row.canMoveDown,
    )
    Spacer(Modifier.width(Spacing.sm))
    AppIconButton(
        contentDescription = stringResource(Res.string.meals_delete_cd, row.label),
        icon = Res.drawable.ic_delete,
        testTag = "meals_delete_${row.key}",
        onClick = { onEvent(MealSlotsEvent.Delete(row.key)) },
        enabled = context.interactive && row.canDelete,
    )
}

@Composable
private fun errorText(error: MealSlotError): String = when (error) {
    MealSlotError.Blank -> stringResource(Res.string.meals_error_blank)
    MealSlotError.TooLong -> stringResource(Res.string.meals_error_too_long)
}
