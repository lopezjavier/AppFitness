@file:Suppress("MatchingDeclarationName") // The file holds the screen and its actions holder.

package io.github.lopezjavier.appfitness.feature.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import io.github.lopezjavier.appfitness.composeapp.resources.action_delete
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.delete_failed
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_deleted
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_delete_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_edit_cd
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_select
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_detail_title
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_in_use_message
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_in_use_routines
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_in_use_sets
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_save_failed
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_history
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_how_to
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_tab_summary
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.ic_delete
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTabRow
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.ExerciseLayoutDefaults
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.designsystem.OfflineHint
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.Equipment
import io.github.lopezjavier.appfitness.domain.model.MovementPattern
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.port.Clock
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
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
    val onEditMovementPattern: (MovementPattern?) -> Unit = {},
    val onSubmitEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

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
    val messages = LocalAppMessages.current
    val haptics = LocalAppHaptics.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // The shell owns the message queue, so "Ejercicio eliminado" outlives this destination.
                ExerciseDetailEffect.Deleted -> {
                    haptics.confirm()
                    messages.show(getString(Res.string.exercise_deleted))
                    currentClose()
                }

                is ExerciseDetailEffect.InUse -> {
                    haptics.reject()
                    messages.show(inUseMessage(effect))
                }

                ExerciseDetailEffect.DeleteFailed -> {
                    haptics.reject()
                    messages.show(getString(Res.string.delete_failed))
                }

                // The sheet already closed and the new values are visible: no message (R-13).
                ExerciseDetailEffect.Saved -> haptics.confirm()

                ExerciseDetailEffect.SaveFailed -> {
                    haptics.reject()
                    messages.show(getString(Res.string.exercise_save_failed))
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
            onEditMovementPattern = viewModel::setEditMovementPattern,
            onSubmitEdit = viewModel::submitEdit,
            onDelete = viewModel::delete,
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
        onClose = onClose,
    )
}

private suspend fun inUseMessage(effect: ExerciseDetailEffect.InUse): String = getString(
    Res.string.exercise_in_use_message,
    getPluralString(Res.plurals.exercise_in_use_sets, effect.sets, effect.sets),
    getPluralString(Res.plurals.exercise_in_use_routines, effect.routines, effect.routines),
)

/**
 * Exercise detail (US-09, US-28) on [AppScreen]: the exercise name as title and, for a manual exercise, edit and
 * delete in the top bar on every tab (delete is immediate, no dialog). Three tabs, "Resumen" (demonstration, header,
 * progress link, records), "Historial" (sessions that contain the exercise) and "Cómo hacerlo" (steps, tips, RepDB
 * attribution). In [selectMode] a "Seleccionar" button is pinned at the bottom of every tab. [today] dates the
 * history rows and the records.
 */
@Composable
@Suppress("LongParameterList") // State, events, navigation hooks and the injectable date.
fun ExerciseDetailScreen(
    state: ExerciseDetailUiState,
    selectMode: Boolean,
    onSelect: () -> Unit,
    onRetry: () -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onOpenProgress: () -> Unit = {},
    actions: ExerciseDetailActions = ExerciseDetailActions(),
    onClose: () -> Unit = {},
) {
    val loaded = state as? ExerciseDetailUiState.Loaded
    val name = loaded?.detail?.name?.ifBlank { null }
    val title = when {
        loaded == null -> stringResource(Res.string.exercise_detail_title)
        else -> name ?: stringResource(Res.string.exercise_unnamed)
    }
    AppScreen(
        title = title,
        modifier = modifier,
        onBack = onClose,
        actions = manageActions(loaded, title, actions),
        footer = if (selectMode && loaded != null) {
            {
                PrimaryButton(
                    text = stringResource(Res.string.exercise_detail_select),
                    onClick = onSelect,
                    modifier = Modifier.fillMaxWidth().testTag("exercise_detail_select_button"),
                )
            }
        } else {
            null
        },
        maxContentWidth = ExerciseLayoutDefaults.ContentMaxWidth,
        testTagPrefix = "exercise_detail",
    ) {
        Box(Modifier.fillMaxSize().testTag("exercise_detail_screen"), contentAlignment = Alignment.TopCenter) {
            when (state) {
                ExerciseDetailUiState.Loading -> DetailLoading()

                ExerciseDetailUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(
                            message = stringResource(Res.string.exercise_error_message),
                            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
                            testTagPrefix = "exercise_detail_error",
                        )
                    }

                is ExerciseDetailUiState.Loaded -> {
                    DetailContent(state, today, onOpenProgress, actions)
                    state.editForm?.let { EditExerciseSheet(it, state.isOnline, actions.toFormActions()) }
                }
            }
        }
    }
}

/** Edit and delete for a manual exercise, disabled (keeping their descriptions) offline or while deleting. */
@Composable
private fun manageActions(
    loaded: ExerciseDetailUiState.Loaded?,
    title: String,
    actions: ExerciseDetailActions,
): List<AppTopBarAction> {
    val editDescription = stringResource(Res.string.exercise_detail_edit_cd, title)
    val deleteDescription = stringResource(Res.string.exercise_detail_delete_cd, title)
    if (loaded == null || !loaded.canManage) return emptyList()
    val enabled = loaded.isOnline && !loaded.isDeleting
    return listOf(
        AppTopBarAction(
            Res.drawable.ic_edit,
            editDescription,
            actions.onOpenEdit,
            testTag = "exercise_detail_edit",
            enabled = enabled,
        ),
        AppTopBarAction(
            Res.drawable.ic_delete,
            deleteDescription,
            actions.onDelete,
            testTag = "exercise_detail_delete",
            enabled = enabled,
        ),
    )
}

private fun ExerciseDetailActions.toFormActions() = ExerciseFormActions(
    onName = onEditName,
    onMuscleGroup = onEditMuscleGroup,
    onEquipment = onEditEquipment,
    onPerHand = onEditPerHand,
    onMovementPattern = onEditMovementPattern,
    onMetricType = {},
    onSubmit = onSubmitEdit,
    onDismiss = onDismissEdit,
)

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
            if (state.canManage && !state.isOnline) {
                OfflineHint(
                    action = stringResource(Res.string.action_delete).lowercase(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenMargin, vertical = Spacing.sm)
                        .testTag("exercise_detail_offline_hint"),
                )
            }
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
