package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_exercise_open_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_group_next
import io.github.lopezjavier.appfitness.composeapp.resources.builder_move_down
import io.github.lopezjavier.appfitness.composeapp.resources.builder_move_up
import io.github.lopezjavier.appfitness.composeapp.resources.builder_remove
import io.github.lopezjavier.appfitness.composeapp.resources.builder_reorder_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_decrease_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_increase_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_rest_unit
import io.github.lopezjavier.appfitness.composeapp.resources.builder_row_cd_superset
import io.github.lopezjavier.appfitness.composeapp.resources.builder_row_menu_cd
import io.github.lopezjavier.appfitness.composeapp.resources.builder_superset_label
import io.github.lopezjavier.appfitness.composeapp.resources.builder_ungroup
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.ic_more_vert
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DragHandle
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnail
import io.github.lopezjavier.appfitness.designsystem.ExerciseThumbnailDefaults
import io.github.lopezjavier.appfitness.designsystem.NumberStepper
import io.github.lopezjavier.appfitness.designsystem.ReorderableItemScope
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.RoutineBounds
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import io.github.lopezjavier.appfitness.feature.workout.restDurationText
import io.github.lopezjavier.appfitness.feature.workout.restStateDescription
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val AccentWidth = Spacing.xs

/** The Spanish name a row shows while its exercise name is unknown. */
@Composable
internal fun BuilderRowUi.displayName(): String = name ?: stringResource(Res.string.exercise_unnamed)

/** "Superserie A1" of a superset member, or null. */
@Composable
internal fun BuilderRowUi.supersetText(): String? =
    supersetLabel?.let { stringResource(Res.string.builder_superset_label, it.letter.toString(), it.number) }

/**
 * One exercise of the routine: drag handle, thumbnail, name, superset label and menu, then the planned sets and the
 * rest stepper. A superset member has a cyan accent on its left edge. [index] is the row position (it names the test
 * tags); Subir and Bajar are also exposed as accessibility actions of the row, so dragging is never the only way to
 * reorder.
 */
@Composable
internal fun ReorderableItemScope.BuilderRow(
    row: BuilderRowUi,
    index: Int,
    onEvent: (RoutineBuilderEvent) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = row.displayName()
    val superset = row.supersetText()
    val moveUp = stringResource(Res.string.builder_move_up)
    val moveDown = stringResource(Res.string.builder_move_down)
    val rowDescription = superset?.let { stringResource(Res.string.builder_row_cd_superset, name, it) } ?: name
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = rowDescription
                customActions =
                    buildList {
                        if (row.canMoveUp) {
                            add(
                                CustomAccessibilityAction(moveUp) {
                                    onEvent(RoutineBuilderEvent.MoveUp(index))
                                    true
                                },
                            )
                        }
                        if (row.canMoveDown) {
                            add(
                                CustomAccessibilityAction(moveDown) {
                                    onEvent(RoutineBuilderEvent.MoveDown(index))
                                    true
                                },
                            )
                        }
                    }
            }
            .testTag("routine_builder_row_$index"),
    ) {
        AppCard(Modifier.fillMaxWidth().padding(start = if (superset != null) AccentWidth else Spacing.none)) {
            RowHeader(row, index, superset, onOpenMenu, onOpenExercise)
            Steppers(row, index, onEvent)
        }
        if (superset != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(AccentWidth)
                    .fillMaxHeight()
                    .background(AppTheme.colors.athleticCyan)
                    .testTag("routine_builder_superset_accent_$index"),
            )
        }
    }
}

@Composable
private fun ReorderableItemScope.RowHeader(
    row: BuilderRowUi,
    index: Int,
    superset: String?,
    onOpenMenu: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    val name = row.displayName()
    val menuLabel = stringResource(Res.string.builder_row_menu_cd, name)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        DragHandle(
            contentDescription = stringResource(Res.string.builder_reorder_cd, name),
            modifier = Modifier.dragHandle().testTag("routine_builder_drag_handle_$index"),
        )
        RowIdentity(row, index, name, superset, onOpenExercise, Modifier.weight(1f))
        Box(
            Modifier
                .size(Spacing.minTouchTarget)
                .clickable(role = Role.Button, onClickLabel = menuLabel, onClick = onOpenMenu)
                .testTag("routine_builder_menu_$index"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more_vert),
                contentDescription = menuLabel,
                tint = AppTheme.colors.textSecondary,
                modifier = Modifier.size(Spacing.xl),
            )
        }
    }
}

/** Thumbnail and name are one tap target that opens the exercise; the handle and the menu stay siblings. */
@Composable
@Suppress("LongParameterList") // Row data, its position, text and tap hook.
private fun RowIdentity(
    row: BuilderRowUi,
    index: Int,
    name: String,
    superset: String?,
    onOpenExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(Res.string.builder_exercise_open_cd, name)
    val key = row.imageKey
    // Thumbnail and name are one tap target that opens the exercise; the handle and the menu stay siblings.
    Row(
        modifier
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpenExercise)
            .testTag("routine_builder_exercise_${index}_open"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ExerciseThumbnail(
            imageUri = key?.let { LocalExerciseMediaUris.current.start(it) },
            size = ExerciseThumbnailDefaults.RowSize,
            testTagPrefix = "routine_builder_row_$index",
        )
        Column(
            Modifier.weight(1f).padding(start = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = name,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (superset != null) {
                Text(
                    text = superset,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.athleticCyan,
                    modifier = Modifier.testTag("routine_builder_superset_label_$index"),
                )
            }
        }
    }
}

@Composable
private fun Steppers(row: BuilderRowUi, index: Int, onEvent: (RoutineBuilderEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlannedSets(row, index, onEvent)
        StepperLine(
            unit = stringResource(Res.string.builder_rest_unit),
            valueText = restDurationText(row.restSeconds),
            state = restStateDescription(row.restSeconds),
            decreaseDescription = stringResource(Res.string.builder_rest_decrease_cd),
            increaseDescription = stringResource(Res.string.builder_rest_increase_cd),
            canDecrease = row.restSeconds > RoutineBounds.REST_MIN_SECONDS,
            canIncrease = row.restSeconds < RoutineBounds.REST_MAX_SECONDS,
            onStep = { onEvent(RoutineBuilderEvent.StepRest(index, it)) },
            tag = "routine_builder_rest_$index",
        )
    }
}

/** Technical label ("SERIES") on the left, the stepper on the right. */
@Suppress("LongParameterList") // Label, value, descriptions and bounds of one stepper.
@Composable
private fun StepperLine(
    unit: String,
    valueText: String,
    state: String,
    decreaseDescription: String,
    increaseDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (Int) -> Unit,
    tag: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = unit,
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        NumberStepper(
            valueText = valueText,
            onDecrement = { onStep(-1) },
            onIncrement = { onStep(1) },
            decrementEnabled = canDecrease,
            incrementEnabled = canIncrease,
            decrementContentDescription = decreaseDescription,
            incrementContentDescription = increaseDescription,
            stateDescription = state,
            testTagPrefix = tag,
        )
    }
}

/** Menu of one row, as a bottom sheet: Subir, Bajar, Agrupar con el siguiente, Desagrupar and Quitar. */
@Composable
internal fun BuilderRowMenu(
    row: BuilderRowUi,
    index: Int,
    onEvent: (RoutineBuilderEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val act: (RoutineBuilderEvent) -> Unit = {
        onEvent(it)
        onDismiss()
    }
    ActionsSheet(title = row.displayName(), onDismiss = onDismiss, testTagPrefix = "routine_builder_menu_sheet") {
        SheetActionRow(
            label = stringResource(Res.string.builder_move_up),
            onClick = { act(RoutineBuilderEvent.MoveUp(index)) },
            testTag = "routine_builder_move_up_$index",
            enabled = row.canMoveUp,
        )
        SheetActionRow(
            label = stringResource(Res.string.builder_move_down),
            onClick = { act(RoutineBuilderEvent.MoveDown(index)) },
            testTag = "routine_builder_move_down_$index",
            enabled = row.canMoveDown,
        )
        SheetActionRow(
            label = stringResource(Res.string.builder_group_next),
            onClick = { act(RoutineBuilderEvent.GroupWithNext(index)) },
            testTag = "routine_builder_group_$index",
            enabled = row.canGroupWithNext,
        )
        if (row.supersetLabel != null) {
            SheetActionRow(
                label = stringResource(Res.string.builder_ungroup),
                onClick = { act(RoutineBuilderEvent.Ungroup(index)) },
                testTag = "routine_builder_ungroup_$index",
            )
        }
        SheetActionRow(
            label = stringResource(Res.string.builder_remove),
            onClick = { act(RoutineBuilderEvent.Remove(index)) },
            testTag = "routine_builder_remove_$index",
            destructive = true,
        )
    }
}
