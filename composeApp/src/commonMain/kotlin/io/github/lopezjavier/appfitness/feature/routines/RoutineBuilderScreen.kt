package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.app.InterceptTopBarBack
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.builder_add_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.builder_discard_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.builder_discard_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.builder_discard_message
import io.github.lopezjavier.appfitness.composeapp.resources.builder_discard_title
import io.github.lopezjavier.appfitness.composeapp.resources.builder_load_error
import io.github.lopezjavier.appfitness.composeapp.resources.builder_name_label
import io.github.lopezjavier.appfitness.composeapp.resources.builder_no_exercises
import io.github.lopezjavier.appfitness.composeapp.resources.builder_offline_message
import io.github.lopezjavier.appfitness.composeapp.resources.builder_save
import io.github.lopezjavier.appfitness.composeapp.resources.builder_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTextField
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.ReorderableColumn
import io.github.lopezjavier.appfitness.designsystem.SetTypePickerSheet
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val SKELETON_ROWS = 2
private val SkeletonRowHeight = Spacing.xl * 7

/**
 * Stateful builder: connects [RoutineBuilderViewModel], hands it the exercise the picker returned
 * ([selectedExerciseId], consumed once through [onConsumeSelection]) and routes its one-off results: close the screen
 * or a snackbar when saving failed.
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
    val snackbarHost = remember { SnackbarHostState() }
    val currentOnClose by rememberUpdatedState(onClose)
    val currentConsumed by rememberUpdatedState(onConsumeSelection)
    val failed = stringResource(Res.string.builder_save_failed)
    val offline = stringResource(Res.string.builder_offline_message)
    LaunchedEffect(selectedExerciseId) {
        if (selectedExerciseId != null) {
            viewModel.onEvent(RoutineBuilderEvent.AddExercise(selectedExerciseId))
            currentConsumed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RoutineBuilderEffect.Close -> currentOnClose()
                is RoutineBuilderEffect.SaveFailed -> snackbarHost.showSnackbar(if (effect.offline) offline else failed)
            }
        }
    }
    RoutineBuilderScreen(state, viewModel::onEvent, onAddExercise, onOpenExercise, modifier, snackbarHost)
}

/**
 * Routine builder (US-10): the name, the exercise cards (reorderable by dragging the handle, or with Subir / Bajar
 * in the row menu) and "Añadir ejercicio", with "Guardar" pinned at the bottom. Back asks before discarding edits.
 */
@Composable
fun RoutineBuilderScreen(
    state: RoutineBuilderUiState,
    onEvent: (RoutineBuilderEvent) -> Unit,
    onAddExercise: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { onEvent(RoutineBuilderEvent.Back) },
    )
    InterceptTopBarBack(enabled = true) { onEvent(RoutineBuilderEvent.Back) }
    Box(modifier.fillMaxSize().testTag("routine_builder_screen"), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state) {
                    RoutineBuilderUiState.Loading -> BuilderLoading()
                    RoutineBuilderUiState.LoadError -> BuilderLoadError { onEvent(RoutineBuilderEvent.Retry) }
                    is RoutineBuilderUiState.Editing -> BuilderForm(state, onEvent, onAddExercise, onOpenExercise)
                }
            }
            SnackbarHost(snackbarHost) { data ->
                Snackbar(Modifier.testTag("routine_builder_snackbar")) { Text(data.visuals.message) }
            }
            if (state is RoutineBuilderUiState.Editing) BuilderFooter(state, onEvent)
        }
    }
    if (state is RoutineBuilderUiState.Editing && state.showDiscardConfirm) {
        AppConfirmDialog(
            title = stringResource(Res.string.builder_discard_title),
            message = stringResource(Res.string.builder_discard_message),
            confirmLabel = stringResource(Res.string.builder_discard_confirm),
            cancelLabel = stringResource(Res.string.builder_discard_cancel),
            onConfirm = { onEvent(RoutineBuilderEvent.ConfirmDiscard) },
            onDismiss = { onEvent(RoutineBuilderEvent.DismissDiscard) },
            testTagPrefix = "routine_builder_discard",
        )
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        AppTextField(
            value = state.name,
            onValueChange = { onEvent(RoutineBuilderEvent.SetName(it)) },
            label = stringResource(Res.string.builder_name_label),
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
        GhostButton(
            text = stringResource(Res.string.builder_add_exercise),
            onClick = onAddExercise,
            modifier = Modifier.fillMaxWidth().testTag("routine_builder_add_exercise_button"),
        )
    }
    BuilderSheets(state, menuRowId, onEvent) { menuRowId = null }
}

/** The set type picker and the row menu, whichever is open. */
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
}

/** "Guardar" pinned at the bottom, with the offline reason above it when there is no connection. */
@Composable
private fun BuilderFooter(state: RoutineBuilderUiState.Editing, onEvent: (RoutineBuilderEvent) -> Unit) {
    Column {
        if (!state.isOnline) {
            OfflineBanner(
                modifier = Modifier.fillMaxWidth(),
                message = stringResource(Res.string.builder_offline_message),
                testTagPrefix = "routine_builder_offline_banner",
            )
        }
        PrimaryButton(
            text = stringResource(Res.string.builder_save),
            onClick = { onEvent(RoutineBuilderEvent.Save) },
            enabled = state.canSave,
            loading = state.isSaving,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("routine_builder_save_button"),
        )
    }
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
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "routine_builder_error",
        )
    }
}
