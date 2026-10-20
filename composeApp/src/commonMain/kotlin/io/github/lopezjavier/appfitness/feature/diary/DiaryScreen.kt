package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_food
import io.github.lopezjavier.appfitness.composeapp.resources.diary_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_settings
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_settings_cd
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.ViewModeSelector
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.DiaryViewMode
import org.jetbrains.compose.resources.stringResource

private val SkeletonDashboardHeight = Spacing.xl * 9
private val SkeletonSectionHeight = Spacing.xl * 3
private const val SKELETON_SECTIONS = 4
private const val SELECTION_BAR_SPACE_UNITS = 3

/**
 * Stateless "Diario" tab (US-19, US-20): the date header, the day's progress and one card per meal, rendered from
 * [state]. Wide windows put the dashboard left of the meals.
 */
@Composable
fun DiaryScreen(
    state: DiaryUiState,
    onEvent: (DiaryEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier.fillMaxSize().testTag("diary_screen")) {
        when (state) {
            DiaryUiState.Loading -> DiaryLoading()

            DiaryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(
                    message = stringResource(Res.string.diary_error_message),
                    action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(DiaryEvent.Retry) },
                    testTagPrefix = "diary_error",
                )
            }

            is DiaryUiState.Content -> DiaryDay(state.content, onEvent)
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            DiarySnackbarHost(snackbarHost, Modifier.padding(Spacing.screenMargin))
            if (state is DiaryUiState.Content && state.content.isSelecting) {
                SelectionActionBar(
                    count = state.content.selectedIds.size,
                    onCopy = { onEvent(DiaryEvent.CopySelected) },
                    onCancel = { onEvent(DiaryEvent.ClearSelection) },
                )
            }
        }
    }
}

/** The undo / failure messages. The action (when any) is a ghost button, 48 dp tall, tagged `diary_undo`. */
@Composable
private fun DiarySnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            action = data.visuals.actionLabel?.let { label ->
                { GhostButton(label, data::performAction, Modifier.testTag("diary_undo")) }
            },
        ) { Text(data.visuals.message) }
    }
}

@Composable
private fun DiaryDay(content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = content.isSelecting,
        onBackCompleted = { onEvent(DiaryEvent.ClearSelection) },
    )
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (windowWidthClassFor(maxWidth) == WindowWidthClass.Wide) {
            Row(Modifier.widthIn(max = WindowWidthClassDefaults.TwoColumnContentMaxWidth).fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(Spacing.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
                ) {
                    DiaryHeader(content, onEvent)
                    DiaryDashboard(content, onEvent)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(Spacing.screenMargin),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    MealSections(content, onEvent)
                    AddFoodButton(onEvent)
                    SelectionBarSpace(content)
                }
            }
        } else {
            Column(
                Modifier.widthIn(max = WindowWidthClassDefaults.CardColumnMaxWidth).fillMaxSize()
                    .verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
                verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
            ) {
                DiaryHeader(content, onEvent)
                DiaryDashboard(content, onEvent)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) { MealSections(content, onEvent) }
                AddFoodButton(onEvent)
                SelectionBarSpace(content)
            }
        }
    }
}

/** Room at the end of the scrolling content so the selection bar never covers the last button. */
@Composable
private fun SelectionBarSpace(content: DiaryContent) {
    if (content.isSelecting) Spacer(Modifier.height(Spacing.xl * SELECTION_BAR_SPACE_UNITS))
}

/** The date selector with its view-mode button (opens the Detalle / Lista / Resumen selector) and "Comidas". */
@Composable
private fun DiaryHeader(content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
    var viewMenuOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        DiaryDateHeader(content.label, content.canGoNext, onEvent, onViewMenu = { viewMenuOpen = !viewMenuOpen })
        if (viewMenuOpen) {
            ViewModeSelector(
                selectedIndex = content.viewMode.ordinal,
                onSelect = {
                    onEvent(DiaryEvent.SetViewMode(DiaryViewMode.entries[it]))
                    viewMenuOpen = false
                },
                testTagPrefix = "diary_view",
            )
        }
        MealSettingsButton(onEvent)
    }
}

@Composable
private fun MealSections(content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
    val actions = EntryActions(
        viewMode = content.viewMode,
        canDelete = !content.isOffline,
        selecting = content.isSelecting,
        selectedIds = content.selectedIds,
        canRepeat = content.canRepeatYesterday,
        onEdit = { onEvent(DiaryEvent.EditEntry(it)) },
        onDelete = { onEvent(DiaryEvent.DeleteEntry(it)) },
        onLongPress = { onEvent(DiaryEvent.EnterSelection(it)) },
        onToggleSelected = { onEvent(DiaryEvent.ToggleSelected(it)) },
        onSelectMeal = { onEvent(DiaryEvent.SelectMeal(it)) },
        onCopyMeal = { onEvent(DiaryEvent.CopyMeal(it)) },
        onRepeat = { onEvent(DiaryEvent.RepeatYesterday(it)) },
    )
    content.meals.forEach { section ->
        MealSectionCard(
            section = section,
            collapsed = section.slot.id in content.collapsedSlotIds,
            actions = actions,
            onToggle = { onEvent(DiaryEvent.ToggleSection(section.slot.id)) },
            onAdd = { onEvent(DiaryEvent.AddToMeal(section.slot.id)) },
        )
    }
}

/** Opens the meals editor ("Comidas") for the visible day. */
@Composable
private fun MealSettingsButton(onEvent: (DiaryEvent) -> Unit) {
    val description = stringResource(Res.string.diary_meal_settings_cd)
    SecondaryButton(
        text = stringResource(Res.string.diary_meal_settings),
        onClick = { onEvent(DiaryEvent.OpenMealSettings) },
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = description
        }.testTag("diary_meal_settings"),
    )
}

/** The Diary-level add action: opens the food search with no meal preselected. */
@Composable
private fun AddFoodButton(onEvent: (DiaryEvent) -> Unit) {
    PrimaryButton(
        text = stringResource(Res.string.diary_add_food),
        onClick = { onEvent(DiaryEvent.AddFood) },
        modifier = Modifier.fillMaxWidth().testTag("diary_add_food"),
    )
}

@Composable
private fun DiaryLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("diary_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonDashboardHeight), AppShapes.lg)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            repeat(SKELETON_SECTIONS) {
                SkeletonBox(Modifier.fillMaxWidth().height(SkeletonSectionHeight), AppShapes.lg)
            }
        }
    }
}
