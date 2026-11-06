@file:Suppress("TooManyFunctions") // One small composable per builder element.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.block_below_recommended
import io.github.lopezjavier.appfitness.composeapp.resources.block_below_recommended_help
import io.github.lopezjavier.appfitness.composeapp.resources.block_below_recommended_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_cardio_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_cardio_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_cardio_own
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_cardio_row
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_cardio_swap
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_cardio_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_day_tab
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_day_total
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_extra_object
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_extra_title
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_helper
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_over
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_plan_closed
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_save
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_save_action
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_save_error
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_section_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_section_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_section_help_strength
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_slot_clear_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_slot_empty
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_slot_supporting
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_slot_swap_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_swap_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_swap_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_swap_marker
import io.github.lopezjavier.appfitness.composeapp.resources.block_builder_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_add
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_swap
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppPickerSheet
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DiscardChangesDialog
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseRow
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.InlineNote
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SheetActionRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.durationText
import io.github.lopezjavier.appfitness.designsystem.supersetText
import io.github.lopezjavier.appfitness.domain.coach.builder.BuilderDay
import io.github.lopezjavier.appfitness.domain.coach.builder.FilledSlot
import io.github.lopezjavier.appfitness.domain.model.CatalogExercise
import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import io.github.lopezjavier.appfitness.domain.usecase.SupersetLabel
import io.github.lopezjavier.appfitness.feature.exercises.LocalExerciseMediaUris
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Desde cero" (US-72, US-93): the three steps (`Splits`, `Types`, `Builder`) of one flow, chosen by [state]. It only
 * renders state and sends [BlockBuilderEvent]s; every rule lives in the ViewModel.
 */
@Composable
internal fun BlockBuilderScreen(
    state: BlockBuilderUiState,
    isOnline: Flow<Boolean>,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is BlockBuilderUiState.Splits -> BlockSplitChoiceScreen(state, onEvent, modifier)

        is BlockBuilderUiState.Types -> BlockTypesScreen(state, onEvent, modifier)

        is BlockBuilderUiState.Builder -> BlockBuilderContent(state, isOnline, onEvent, onOpenExercise, modifier)

        is BlockBuilderUiState.Targets -> BlockTargetsScreen(state, onEvent, modifier)

        BlockBuilderUiState.Loading, is BlockBuilderUiState.Failed ->
            BuilderPlaceholderScreen(state, isOnline, onEvent, modifier)
    }
}

@Composable
private fun BuilderPlaceholderScreen(
    state: BlockBuilderUiState,
    isOnline: Flow<Boolean>,
    onEvent: (BlockBuilderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.block_builder_title),
        modifier = modifier,
        onBack = { onEvent(BlockBuilderEvent.Back) },
        testTagPrefix = "block_builder_start",
    ) {
        when (state) {
            is BlockBuilderUiState.Failed -> if (state.offline) {
                OfflineState(isOnline = isOnline, onReload = { onEvent(BlockBuilderEvent.Retry) })
            } else {
                ErrorState(
                    message = stringResource(Res.string.block_builder_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) {
                        onEvent(BlockBuilderEvent.Retry)
                    },
                    testTagPrefix = "block_builder_error",
                    actionTestTag = "block_builder_retry",
                )
            }

            else -> Column(
                Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("block_builder_loading"),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                repeat(LOADING_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget)) }
            }
        }
    }
}

private const val LOADING_ROWS = 4

@Composable
private fun BlockBuilderContent(
    state: BlockBuilderUiState.Builder,
    isOnline: Flow<Boolean>,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val online by isOnline.collectAsStateWithLifecycle(initialValue = true)
    var cardioDay by rememberSaveable { mutableStateOf<Int?>(null) }
    val days = state.state.days
    val tabs = days.indices.map {
        AppOption(stringResource(Res.string.block_builder_day_tab, it + 1), "block_builder_day_tab_$it")
    }
    AppScreen(
        title = stringResource(Res.string.block_builder_title),
        modifier = modifier,
        onBack = { onEvent(BlockBuilderEvent.Back) },
        footer = { BuilderFooter(state, online, onEvent) },
        testTagPrefix = "block_builder",
    ) {
        Column(Modifier.fillMaxSize()) {
            AppTabRow(tabs, state.selectedDay, { onEvent(BlockBuilderEvent.SelectDay(it)) })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DayContent(state, state.selectedDay, onEvent, onOpenExercise) { cardioDay = it }
            }
        }
    }
    state.openSlot?.let { BuilderSlotPickerSheet(state, it, onEvent, onOpenExercise, online) }
    if (state.discardDialog) {
        DiscardChangesDialog(
            onConfirm = { onEvent(BlockBuilderEvent.ConfirmDiscard) },
            onDismiss = { onEvent(BlockBuilderEvent.CancelDiscard) },
            testTagPrefix = "block_builder_discard",
        )
    }
    cardioDay?.let {
        AppPickerSheet(
            title = stringResource(Res.string.block_builder_cardio_swap),
            onDismiss = { cardioDay = null },
            testTagPrefix = "block_builder_cardio_sheet",
        ) {
            state.cardioOptions.forEach { option ->
                SheetActionRow(
                    label = option.name,
                    onClick = {
                        cardioDay = null
                        onEvent(BlockBuilderEvent.SwapCardio(option.id))
                    },
                    testTag = "block_builder_cardio_option_${option.id}",
                )
            }
        }
    }
}

@Composable
private fun DayContent(
    ui: BlockBuilderUiState.Builder,
    dayIndex: Int,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    onSwapCardio: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val state = ui.state
        val day = state.days[dayIndex]
        val cardioMin = state.cardioPlan.perRoutineMin[dayIndex]
        ListSectionHeader(day.name, Modifier.testTag("block_builder_day_title_$dayIndex"))
        Column(Modifier.testTag("block_builder_day_total_$dayIndex")) {
            Text(
                stringResource(
                    Res.string.block_builder_day_total,
                    durationText((state.sessionMinutes + cardioMin) * SECONDS),
                ),
            )
            val over = state.extrasOverMinutes(dayIndex)
            if (over > 0) Text(stringResource(Res.string.block_builder_over, over))
        }
        val strength = state.intention == TrainingIntention.Strength
        day.sections().forEach { section ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    sectionHeaderText(section),
                    Modifier.weight(1f).testTag("block_builder_section_${dayIndex}_${section.key}"),
                )
                HelpButton(
                    contentDescription = stringResource(
                        Res.string.block_builder_section_help_cd,
                        sectionTitle(section),
                    ),
                    title = sectionTitle(section),
                    body = stringResource(
                        if (strength) {
                            Res.string.block_builder_section_help_strength
                        } else {
                            Res.string.block_builder_section_help_body
                        },
                    ),
                    testTag = "block_builder_section_help_${dayIndex}_${section.key}",
                )
            }
            section.slots.forEach { (index, filled) ->
                SlotRow(ui, dayIndex, index, day, filled, onEvent, onOpenExercise)
            }
        }
        ExtraSection(ui, dayIndex, day, onEvent, onOpenExercise)
        if (cardioMin > 0) CardioSection(ui, dayIndex, cardioMin, onSwapCardio)
        val own = state.cardioPlan.onYourOwnMin
        if (own > 0) {
            InlineNote(
                text = stringResource(Res.string.block_builder_cardio_own, own),
                modifier = Modifier.testTag("block_builder_cardio_own"),
            )
        }
    }
}

private const val SECONDS = 60L

@Composable
private fun Text(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Suppress("LongParameterList") // The row of one slot needs its day, position, pick and the two callbacks.
@Composable
private fun SlotRow(
    ui: BlockBuilderUiState.Builder,
    dayIndex: Int,
    index: Int,
    day: BuilderDay,
    filled: FilledSlot,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val pick = filled.pick
    val open = { onEvent(BlockBuilderEvent.OpenSlot(SlotRef(dayIndex, index))) }
    val tag = "block_builder_slot_${dayIndex}_$index"
    if (pick == null) {
        AppListRow(
            headline = stringResource(Res.string.block_builder_slot_empty),
            supporting = setsText(filled.slot.sets),
            leading = { Icon(painterResource(Res.drawable.ic_add), null, tint = AppTheme.colors.textSecondary) },
            onClick = open,
            modifier = Modifier.testTag(tag),
        )
        return
    }
    val exercise = ui.exercises[pick.exerciseId] ?: return
    val name = exercise.name
    val weight = pick.plannedWeightKg?.let {
        stringResource(Res.string.block_builder_slot_supporting, setsText(filled.slot.sets), weightText(it))
    }
    val superset = day.supersets.withIndex().firstNotNullOfOrNull { (n, pair) ->
        when (index) {
            pair.first -> SupersetLabel('A' + n, 1)
            pair.second -> SupersetLabel('A' + n, 2)
            else -> null
        }
    }
    val base = weight ?: setsText(filled.slot.sets)
    val supporting = if (superset != null) "${supersetText(superset)} · $base" else base
    val stalled = pick.exerciseId in ui.stalledExerciseIds
    val swapMarker = stringResource(Res.string.block_builder_swap_marker)
    ExerciseRow(
        name = name,
        supporting = supporting,
        onOpen = { onOpenExercise(pick.exerciseId) },
        tagPrefix = tag,
        imageUri = exercise.rowImageUri(),
        trailing = { SlotActions(name, tag, dayIndex, index, stalled, open, onEvent) },
    )
    if (stalled) Text(swapMarker, Modifier.padding(start = Spacing.screenMargin).testTag("${tag}_swap_marker"))
}

@Suppress("LongParameterList") // The actions of one filled slot.
@Composable
private fun SlotActions(
    name: String,
    tag: String,
    dayIndex: Int,
    index: Int,
    stalled: Boolean,
    open: () -> Unit,
    onEvent: (BlockBuilderEvent) -> Unit,
) {
    Row {
        if (stalled) {
            HelpButton(
                contentDescription = stringResource(Res.string.block_builder_swap_help_cd),
                title = stringResource(Res.string.block_builder_swap_marker),
                body = stringResource(Res.string.block_builder_swap_help_body),
                testTag = "${tag}_swap_help",
            )
        }
        AppIconButton(
            contentDescription = stringResource(Res.string.block_builder_slot_swap_cd, name),
            icon = Res.drawable.ic_swap,
            testTag = "block_builder_slot_swap_${dayIndex}_$index",
            onClick = open,
            tint = AppTheme.colors.textSecondary,
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.block_builder_slot_clear_cd, name),
            icon = Res.drawable.ic_close,
            testTag = "block_builder_slot_clear_${dayIndex}_$index",
            onClick = { onEvent(BlockBuilderEvent.ClearSlot(dayIndex, index)) },
            tint = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ExtraSection(
    ui: BlockBuilderUiState.Builder,
    dayIndex: Int,
    day: BuilderDay,
    onEvent: (BlockBuilderEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    ListSectionHeader(stringResource(Res.string.block_builder_extra_title))
    day.extras.forEachIndexed { index, extra ->
        val pick = extra.pick ?: return@forEachIndexed
        val exercise = ui.exercises[pick.exerciseId] ?: return@forEachIndexed
        val name = exercise.name
        ExerciseRow(
            name = name,
            supporting = setsText(extra.slot.sets),
            onOpen = { onOpenExercise(pick.exerciseId) },
            tagPrefix = "block_builder_extra_${dayIndex}_$index",
            imageUri = exercise.rowImageUri(),
            trailing = {
                AppIconButton(
                    contentDescription = stringResource(Res.string.block_builder_slot_clear_cd, name),
                    icon = Res.drawable.ic_close,
                    testTag = "block_builder_extra_remove_${dayIndex}_$index",
                    onClick = { onEvent(BlockBuilderEvent.RemoveExtra(dayIndex, index)) },
                    tint = AppTheme.colors.textSecondary,
                )
            },
        )
    }
    AddItemButton(
        objectLabel = stringResource(Res.string.block_builder_extra_object),
        onClick = { onEvent(BlockBuilderEvent.AddExtra(dayIndex)) },
        modifier = Modifier.testTag("block_builder_extra_add_$dayIndex"),
    )
}

@Composable
private fun CardioSection(ui: BlockBuilderUiState.Builder, dayIndex: Int, minutes: Int, onSwapCardio: (Int) -> Unit) {
    ListSectionHeader(stringResource(Res.string.block_builder_cardio_title))
    val cardio = ui.cardioExercise ?: return
    val name = cardio.name
    AppListRow(
        headline = stringResource(Res.string.block_builder_cardio_row, name, minutes),
        modifier = Modifier.testTag("block_builder_cardio_$dayIndex"),
        trailing = {
            Row {
                HelpButton(
                    contentDescription = stringResource(Res.string.block_builder_cardio_help_cd),
                    title = stringResource(Res.string.block_builder_cardio_title),
                    body = stringResource(Res.string.block_builder_cardio_help_body),
                    testTag = "block_builder_cardio_help_$dayIndex",
                )
                AppIconButton(
                    contentDescription = stringResource(Res.string.block_builder_cardio_swap),
                    icon = Res.drawable.ic_swap,
                    testTag = "block_builder_cardio_swap_$dayIndex",
                    onClick = { onSwapCardio(dayIndex) },
                    tint = AppTheme.colors.textSecondary,
                )
            }
        },
    )
}

/** The score bar with its text, the notes and "Guardar bloque" (R-09: one pinned footer for the whole block). */
@Composable
private fun BuilderFooter(state: BlockBuilderUiState.Builder, online: Boolean, onEvent: (BlockBuilderEvent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        BuilderScoreBar(state.state.score.percent, "block_builder_score")
        if (state.state.days.any { it.cutSets }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                InlineNote(stringResource(Res.string.block_below_recommended), Modifier.weight(1f))
                HelpButton(
                    contentDescription = stringResource(Res.string.block_below_recommended_help_cd),
                    title = stringResource(Res.string.block_below_recommended),
                    body = stringResource(Res.string.block_below_recommended_help),
                    testTag = "block_builder_below_note_help",
                )
            }
        }
        if (!state.state.canSave) {
            Text(stringResource(Res.string.block_builder_helper), Modifier.testTag("block_builder_helper"))
        }
        when (state.saveError) {
            BuilderSaveError.Generic, BuilderSaveError.Offline -> InlineNote(
                stringResource(Res.string.block_builder_save_error),
                Modifier.testTag("block_builder_save_error"),
            )

            BuilderSaveError.PlanClosed -> InlineNote(
                stringResource(Res.string.block_builder_plan_closed),
                Modifier.testTag("block_builder_save_error"),
            )

            null -> Unit
        }
        PrimaryButton(
            text = stringResource(Res.string.block_builder_save),
            onClick = { onEvent(BlockBuilderEvent.Save) },
            modifier = Modifier.fillMaxWidth().testTag("block_builder_save"),
            enabled = state.canSave && online,
            loading = state.saving,
        )
        if (!online) OfflineHint(stringResource(Res.string.block_builder_save_action))
    }
}

/** The start picture of this exercise for a row, or null when the library has none. */
@Composable
internal fun CatalogExercise.rowImageUri(): String? {
    val key = exercise.key
    return if (hasMedia && key != null) LocalExerciseMediaUris.current.start(key) else null
}
