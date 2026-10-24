@file:Suppress("MatchingDeclarationName") // The file holds the screen and its actions holder.

package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_delete_cancel
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_delete_confirm
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_delete_deleting
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_delete_error
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_delete_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_delete_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_select
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_in_use_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_in_use_routines
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_in_use_sets
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_history
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_how_to
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_summary
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppConfirmDialog
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.AppError
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the detail can ask of its ViewModel besides the load retry; every action defaults to a no-op. */
class ExerciseDetailActions(
    val onSelectTab: (ExerciseDetailTab) -> Unit = {},
    val onRetryRecords: () -> Unit = {},
    val onLoadMoreHistory: () -> Unit = {},
    val onRetryHistory: () -> Unit = {},
    val onRetryHistoryPage: () -> Unit = {},
    val onOpenEdit: () -> Unit = {},
    val onDismissEdit: () -> Unit = {},
    val onEditName: (String) -> Unit = {},
    val onEditMuscleGroup: (MuscleGroup) -> Unit = {},
    val onEditEquipment: (Equipment) -> Unit = {},
    val onEditPerHand: (Boolean) -> Unit = {},
    val onSubmitEdit: () -> Unit = {},
    val onOpenDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onDismissInUse: () -> Unit = {},
)

/** Carries "Ejercicio eliminado" from the detail that deleted an exercise to the list it returns to. */
object ExerciseDeletedNotice {
    private val pendingFlow = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = pendingFlow.asStateFlow()

    fun post() {
        pendingFlow.value = true
    }

    fun consume() {
        pendingFlow.value = false
    }
}

/** Stateful detail: connects [ExerciseDetailViewModel] to the stateless [ExerciseDetailScreen]. */
@Composable
fun ExerciseDetailRoot(
    exerciseId: String,
    selectMode: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    onClose: () -> Unit = {},
    viewModel: ExerciseDetailViewModel = koinViewModel(key = exerciseId, parameters = { parametersOf(exerciseId) }),
    clock: Clock = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ExerciseDetailEffect.Deleted -> {
                    ExerciseDeletedNotice.post()
                    currentClose()
                }
            }
        }
    }
    val actions = remember(viewModel) {
        ExerciseDetailActions(
            onSelectTab = viewModel::selectTab,
            onRetryRecords = viewModel::retryRecords,
            onLoadMoreHistory = viewModel::loadMoreHistory,
            onRetryHistory = viewModel::retryHistory,
            onRetryHistoryPage = viewModel::retryHistoryPage,
            onOpenEdit = viewModel::openEdit,
            onDismissEdit = viewModel::dismissEdit,
            onEditName = viewModel::setEditName,
            onEditMuscleGroup = viewModel::selectEditMuscleGroup,
            onEditEquipment = viewModel::selectEditEquipment,
            onEditPerHand = viewModel::setEditPerHand,
            onSubmitEdit = viewModel::submitEdit,
            onOpenDelete = viewModel::openDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
            onDismissInUse = viewModel::dismissInUse,
        )
    }
    ExerciseDetailScreen(
        state = state,
        selectMode = selectMode,
        onSelect = onSelect,
        onRetry = viewModel::retry,
        today = clock.today(),
        modifier = modifier,
        onOpenProgress = onOpenProgress,
        actions = actions,
    )
}

/**
 * Exercise detail (US-09, US-28): three tabs, "Resumen" (demonstration, header, progress link, records), "Historial"
 * (sessions that contain the exercise) and "Cómo hacerlo" (steps, tips, RepDB attribution). In [selectMode] a
 * "Seleccionar" button is pinned at the bottom of every tab. [today] dates the history rows and the records.
 */
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    selectMode: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    actions: ExerciseDetailActions = ExerciseDetailActions(),
) {
    Column(modifier.fillMaxSize().testTag("exercise_detail_screen")) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (state) {
                ExerciseDetailUiState.Loading -> DetailLoading()

                ExerciseDetailUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(
                            message = stringResource(Res.string.exercise_error_message),
                            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
                            testTagPrefix = "exercise_detail_error",
                        )
                    }

                is ExerciseDetailUiState.Loaded -> {
                    Box(Modifier.fillMaxSize()) {
                        DetailContent(state, today, onOpenProgress, actions)
                    }
                    ManageOverlays(state, actions)
                }
            }
        }
        if (selectMode && state is ExerciseDetailUiState.Loaded) {
            PrimaryButton(
                text = stringResource(Res.string.exercise_detail_select),
                onClick = onSelect,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                    .testTag("exercise_detail_select_button"),
            )
        }
    }
}

@Composable
private fun DetailLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().widthIn(max = ExerciseLayoutDefaults.AnimationMaxWidth).padding(Spacing.xl))
        SkeletonBox(Modifier.fillMaxWidth(NAME_FRACTION).padding(Spacing.lg))
    }
}

private const val NAME_FRACTION = 0.6f

/** "Editar" and "Eliminar" icon buttons (48 dp each) in the exercise name row; manual exercises only. */
@Composable
private fun ManageActions(actions: ExerciseDetailActions) {
    Row {
        AppIconButton(
            contentDescription = stringResource(Res.string.exercise_detail_edit_cd),
            icon = Res.drawable.ic_edit,
            testTag = "exercise_detail_edit",
            onClick = actions.onOpenEdit,
        )
        AppIconButton(
            contentDescription = stringResource(Res.string.exercise_detail_delete_cd),
            icon = Res.drawable.ic_delete,
            testTag = "exercise_detail_delete",
            onClick = actions.onOpenDelete,
            tint = AppTheme.colors.error,
        )
    }
}

/** The edit sheet, the delete confirmation and the "en uso" snackbar of a manual exercise. */
@Composable
private fun ManageOverlays(state: ExerciseDetailUiState.Loaded, actions: ExerciseDetailActions) {
    state.editForm?.let { form ->
        EditExerciseSheet(
            form = form,
            actions = ExerciseFormActions(
                onName = actions.onEditName,
                onMuscleGroup = actions.onEditMuscleGroup,
                onEquipment = actions.onEditEquipment,
                onPerHand = actions.onEditPerHand,
                onMetricType = {},
                onSubmit = actions.onSubmitEdit,
                onDismiss = actions.onDismissEdit,
            ),
        )
    }
    state.deleteDialog?.let { dialog ->
        AppConfirmDialog(
            title = stringResource(Res.string.exercise_delete_title),
            message = if (dialog.hasError) {
                stringResource(Res.string.exercise_delete_error)
            } else {
                stringResource(Res.string.exercise_delete_message, state.detail.name)
            },
            confirmLabel = stringResource(
                if (dialog.isDeleting) Res.string.exercise_delete_deleting else Res.string.exercise_delete_confirm,
            ),
            cancelLabel = stringResource(Res.string.exercise_delete_cancel),
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDelete,
            testTagPrefix = "exercise_detail_delete",
        )
    }
    state.inUse?.let { inUse -> InUseMessage(inUse, actions.onDismissInUse) }
}

@Composable
private fun InUseMessage(inUse: AppError.InUse, onDismiss: () -> Unit) {
    val message = stringResource(
        Res.string.exercise_in_use_message,
        pluralStringResource(Res.plurals.exercise_in_use_sets, inUse.sets, inUse.sets),
        pluralStringResource(Res.plurals.exercise_in_use_routines, inUse.routines, inUse.routines),
    )
    val host = remember { SnackbarHostState() }
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(inUse) {
        host.showSnackbar(message)
        currentDismiss()
    }
    Box(Modifier.fillMaxSize().padding(Spacing.screenMargin), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(host) { data ->
            Snackbar {
                Text(data.visuals.message, Modifier.testTag("exercise_detail_in_use_message"))
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: ExerciseDetailUiState.Loaded,
    today: LocalDate,
    onOpenProgress: () -> Unit,
    actions: ExerciseDetailActions,
) {
    // Each tab keeps its own scroll state; they are hoisted here so a tab change does not reset the others.
    val summaryScroll = rememberScrollState()
    val howToScroll = rememberScrollState()
    val historyList = rememberLazyListState()
    val options = tabOptions()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppTabRow(
                options = options,
                selectedIndex = state.tab.ordinal,
                onSelect = { actions.onSelectTab(ExerciseDetailTab.entries[it]) },
                modifier = Modifier.widthIn(
                    max = ExerciseLayoutDefaults.ContentMaxWidth,
                ).testTag("exercise_detail_tabs"),
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when (state.tab) {
                    ExerciseDetailTab.Summary -> ExerciseSummaryTab(
                        detail = state.detail,
                        records = state.records,
                        today = today,
                        wide = wide,
                        onOpenProgress = onOpenProgress,
                        onRetryRecords = actions.onRetryRecords,
                        modifier = Modifier.fillMaxSize(),
                        scrollState = summaryScroll,
                        nameActions = if (state.canManage) {
                            { ManageActions(actions) }
                        } else {
                            null
                        },
                    )

                    ExerciseDetailTab.History -> ExerciseHistoryTab(
                        state = state.history,
                        today = today,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                        listState = historyList,
                    )

                    ExerciseDetailTab.HowTo -> ExerciseHowToTab(
                        detail = state.detail,
                        modifier = Modifier.fillMaxSize(),
                        scrollState = howToScroll,
                    )
                }
            }
        }
    }
}

@Composable
private fun tabOptions(): List<AppOption> {
    val summary = stringResource(Res.string.exercise_tab_summary)
    val history = stringResource(Res.string.exercise_tab_history)
    val howTo = stringResource(Res.string.exercise_tab_how_to)
    return remember(summary, history, howTo) {
        listOf(
            AppOption(summary, "exercise_detail_tab_summary"),
            AppOption(history, "exercise_detail_tab_history"),
            AppOption(howTo, "exercise_detail_tab_how_to"),
        )
    }
}
