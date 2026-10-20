package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_add_food
import io.github.lopezjavier.appfitness.composeapp.resources.diary_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_settings
import io.github.lopezjavier.appfitness.composeapp.resources.diary_meal_settings_cd
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import org.jetbrains.compose.resources.stringResource

private val SkeletonDashboardHeight = Spacing.xl * 9
private val SkeletonSectionHeight = Spacing.xl * 3
private const val SKELETON_SECTIONS = 4

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
        DiarySnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).padding(Spacing.screenMargin))
    }
}

/** The failure messages (a failed or refused write). */
@Composable
private fun DiarySnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> Snackbar { Text(data.visuals.message) } }
}

@Composable
private fun DiaryDay(content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
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
            }
        }
    }
}

/** The date selector and the "Comidas" button. */
@Composable
private fun DiaryHeader(content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        DiaryDateHeader(content.label, content.canGoNext, onEvent)
        MealSettingsButton(onEvent)
    }
}

@Composable
private fun MealSections(content: DiaryContent, onEvent: (DiaryEvent) -> Unit) {
    val actions = EntryActions(
        canDelete = !content.isOffline,
        onEdit = { onEvent(DiaryEvent.EditEntry(it)) },
        onDelete = { onEvent(DiaryEvent.DeleteEntry(it)) },
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
