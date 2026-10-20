package io.github.lopezjavier.appfitness.feature.meals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_downward
import io.github.lopezjavier.appfitness.composeapp.resources.ic_arrow_upward
import io.github.lopezjavier.appfitness.composeapp.resources.meals_action_down
import io.github.lopezjavier.appfitness.composeapp.resources.meals_action_up
import io.github.lopezjavier.appfitness.composeapp.resources.meals_add_after_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_error_blank
import io.github.lopezjavier.appfitness.composeapp.resources.meals_error_too_long
import io.github.lopezjavier.appfitness.composeapp.resources.meals_hint_day
import io.github.lopezjavier.appfitness.composeapp.resources.meals_hint_today
import io.github.lopezjavier.appfitness.composeapp.resources.meals_hint_unknown
import io.github.lopezjavier.appfitness.composeapp.resources.meals_label_field
import io.github.lopezjavier.appfitness.composeapp.resources.meals_move_down_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_move_up_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_reorder_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_switch_cd
import io.github.lopezjavier.appfitness.composeapp.resources.meals_switch_off
import io.github.lopezjavier.appfitness.composeapp.resources.meals_switch_on
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppSwitch
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.usecase.MealSlotError
import org.jetbrains.compose.resources.stringResource

/** Opacity of the label line of a meal that is switched off: dimmed, still readable. */
private const val DIMMED_ALPHA = 0.7f

/** What a row needs besides its own state: whether the editor is busy and what day the "has entries" hint names. */
internal data class MealRowContext(val interactive: Boolean, val viewedDayIsToday: Boolean)

/**
 * One meal card: the drag handle and the name field on the first line, the hint when it cannot be switched off, and
 * "Subir" / "Bajar" / "Añadir después" / the switch on the second line (so a narrow window or a large font never
 * squeezes the field). Subir and Bajar are also custom accessibility actions of the card.
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
    AppCard(
        modifier
            .fillMaxWidth()
            .testTag("meals_row_${row.key}")
            .semantics {
                customActions = moveActions(row, index, upLabel, downLabel, onEvent)
            },
    ) {
        Row(
            Modifier.fillMaxWidth().alpha(if (row.enabled) 1f else DIMMED_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            DragHandle(
                contentDescription = stringResource(Res.string.meals_reorder_cd, row.label),
                modifier = Modifier.dragHandle().testTag("meals_handle_${row.key}"),
            )
            AppTextField(
                value = row.label,
                onValueChange = { onEvent(MealSlotsEvent.SetLabel(row.key, it)) },
                label = stringResource(Res.string.meals_label_field),
                modifier = Modifier.weight(1f),
                errorText = row.error?.let { errorText(it) },
                enabled = context.interactive,
                testTagPrefix = "meals_label_${row.key}",
            )
        }
        HintLine(row, context)
        MealControls(row, index, context, onEvent)
    }
}

/** Subir / Bajar as accessibility actions of the card, only the ones that make sense for the row. */
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

/** Second line of the card: move up, move down, add after, and the switch at the end. */
@Composable
private fun MealControls(row: MealSlotRowUi, index: Int, context: MealRowContext, onEvent: (MealSlotsEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
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
        AppIconButton(
            contentDescription = stringResource(Res.string.meals_add_after_cd, row.label),
            icon = Res.drawable.ic_add,
            testTag = "meals_add_after_${row.key}",
            onClick = { onEvent(MealSlotsEvent.Add(afterIndex = index)) },
            enabled = context.interactive,
        )
        Spacer(Modifier.weight(1f))
        AppSwitch(
            checked = row.enabled,
            onCheckedChange = { onEvent(MealSlotsEvent.SetEnabled(row.key, it)) },
            contentDescription = stringResource(Res.string.meals_switch_cd, row.label),
            modifier = Modifier.testTag("meals_switch_${row.key}"),
            enabled = context.interactive && row.canToggle,
            stateDescription = stringResource(
                if (row.enabled) Res.string.meals_switch_on else Res.string.meals_switch_off,
            ),
        )
    }
}

/** Why the meal cannot be switched off right now; nothing when it can. */
@Composable
private fun HintLine(row: MealSlotRowUi, context: MealRowContext) {
    if (row.canToggle) return
    val text =
        when {
            !row.hasEntriesInViewedDay -> stringResource(Res.string.meals_hint_unknown)
            context.viewedDayIsToday -> stringResource(Res.string.meals_hint_today)
            else -> stringResource(Res.string.meals_hint_day)
        }
    Text(
        text = text,
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.padding(top = Spacing.xs).testTag("meals_hint_${row.key}"),
    )
}

@Composable
private fun errorText(error: MealSlotError): String = when (error) {
    MealSlotError.Blank -> stringResource(Res.string.meals_error_blank)
    MealSlotError.TooLong -> stringResource(Res.string.meals_error_too_long)
}
