package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.builder_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.builder_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.builder_name_taken
import io.github.lopezjavier.appfitness.composeapp.resources.builder_no_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.builder_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.builder_title_edit
import io.github.lopezjavier.appfitness.composeapp.resources.builder_title_new
import io.github.lopezjavier.appfitness.designsystem.AddItemButton
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.ReorderableColumn
import io.github.lopezjavier.appfitness.designsystem.SetTypePickerSheet
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.offlineHint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val SKELETON_ROWS = 2
private val SkeletonRowHeight = Spacing.xl * 7

/**
 * Stateful builder: connects [RoutineBuilderViewModel], hands it the exercise the picker returned
 * ([selectedExerciseId], consumed once through [onConsumeSelection]) and routes its one-off results: a Reject haptic
 * and a message when saving failed. Closing after a save is the editor template's job (it reads `saved`).
 */
@Composable
@Suppress("LongParameterList") // Navigation hooks of the screen.
fun RoutineBuilderRoot(
    routineId: String?,
    selectedExerciseId: String?,
    onConsumeSelection: () -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineBuilderViewModel = koinViewModel(parameters = { parametersOf(routineId.orEmpty()) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentConsumed by rememberUpdatedState(onConsumeSelection)
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    val failed = stringResource(Res.string.builder_save_failed)
    val offline = offlineHint("guardar")
    LaunchedEffect(selectedExerciseId) {
        if (selectedExerciseId != null) {
            viewModel.onEvent(RoutineBuilderEvent.AddExercise(selectedExerciseId))
            currentConsumed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RoutineBuilderEffect.SaveFailed -> {
                    haptics.reject()
                    messages.show(if (effect.offline) offline else failed)
                }
            }
        }
    }
    RoutineBuilderScreen(state, viewModel::onEvent, onAddExercise, onOpenExercise, modifier, onClose)
}

/**
 * Routine builder (US-10) on the editor template: the name, the exercise cards (reorderable by dragging the handle,
 * swipeable by their header, or with Subir / Bajar / Quitar in the row menu) and "Añadir ejercicio" at the end.
 * The template owns "Guardar", the offline hint and the discard dialog.
 */
@Composable
fun RoutineBuilderScreen(
    state: RoutineBuilderUiState,
    onEvent: (RoutineBuilderEvent) -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val editing = state as? RoutineBuilderUiState.Editing
    AppEditorScreen(
        title = stringResource(
            if (editing?.isEditMode == false) Res.string.builder_title_new else Res.string.builder_title_edit,
        ),
        isDirty = editing?.isDirty == true,
        isSaving = editing?.isSaving == true,
        isOnline = editing?.isOnline ?: true,
        onSave = { onEvent(RoutineBuilderEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = editing?.saved == true,
        canSave = editing?.canSave == true,
        testTagPrefix = "routine_builder",
    ) {
        Box(Modifier.fillMaxSize().testTag("routine_builder_screen")) {
            when (state) {
                RoutineBuilderUiState.Loading -> BuilderLoading()
                RoutineBuilderUiState.LoadError -> BuilderLoadError { onEvent(RoutineBuilderEvent.Retry) }
                is RoutineBuilderUiState.Editing -> BuilderForm(state, onEvent, onAddExercise, onOpenExercise)
            }
        }
    }
}

@Composable
private fun BuilderForm(
    state: RoutineBuilderUiState.Editing,
    onEvent: (RoutineBuilderEvent) -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    // The row whose menu sheet is open, by its stable id (positions change while editing).
    var menuRowId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        AppTextField(
            value = state.name,
            onValueChange = { onEvent(RoutineBuilderEvent.SetName(it)) },
            label = stringResource(Res.string.builder_name_label),
            errorText = if (state.nameTaken) stringResource(Res.string.builder_name_taken) else null,
            testTagPrefix = "routine_builder_name_field",
        )
        if (state.rows.isEmpty()) {
            Text(
                text = stringResource(Res.string.builder_no_exercises),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("routine_builder_empty_hint"),
            )
        } else {
            ReorderableColumn(
                items = state.rows,
                key = { it.rowId },
                onMove = { from, to -> onEvent(RoutineBuilderEvent.MoveTo(from, to)) },
                modifier = Modifier.fillMaxWidth(),
                spacing = Spacing.sm,
                onAutoScroll = { scrollState.scrollBy(it) },
            ) { row, index, _ ->
                // Rows of one superset sit 8 dp apart; every other row gets 12 dp from the previous one.
                val continuesGroup = (row.supersetLabel?.number ?: 1) > 1
                BuilderRow(
                    row = row,
                    index = index,
                    onEvent = onEvent,
                    onOpenMenu = { menuRowId = row.rowId },
                    onOpenExercise = { onOpenExercise(row.exerciseId) },
                    modifier = Modifier.padding(top = if (index > 0 && !continuesGroup) Spacing.xs else Spacing.none),
                )
            }
        }
        AddItemButton(
            objectLabel = "ejercicio",
            onClick = onAddExercise,
            modifier = Modifier.testTag("routine_builder_add_exercise"),
        )
    }
    BuilderSheets(state, menuRowId, onEvent) { menuRowId = null }
}

/** The set type picker, the row menu and the calculator / double-dumbbell sheets, whichever is open. */
@Composable
private fun BuilderSheets(
    state: RoutineBuilderUiState.Editing,
    menuRowId: Long?,
    onEvent: (RoutineBuilderEvent) -> Unit,
    onCloseMenu: () -> Unit,
) {
    state.typePicker?.let { picker ->
        SetTypePickerSheet(
            current = picker.current,
            onSelect = { onEvent(RoutineBuilderEvent.ChangeSetType(picker.rowIndex, picker.setIndex, it)) },
            onDismiss = { onEvent(RoutineBuilderEvent.CloseTypePicker) },
        )
    }
    val menuIndex = state.rows.indexOfFirst { it.rowId == menuRowId }
    if (menuIndex >= 0) {
        BuilderRowMenu(row = state.rows[menuIndex], index = menuIndex, onEvent = onEvent, onDismiss = onCloseMenu)
    }
    BuilderOptionSheets(state, onEvent)
}

@Composable
private fun BuilderLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("routine_builder_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        repeat(SKELETON_ROWS) { SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.lg) }
    }
}

@Composable
private fun BuilderLoadError(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.builder_load_error),
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            testTagPrefix = "routine_builder_error",
        )
    }
}
