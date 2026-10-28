package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.diary_defaults_failed
import io.github.lopezjavier.appfitness.composeapp.resources.diary_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.appAnimateItem
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.feature.notifications.notificationsBarAction
import io.github.lopezjavier.appfitness.feature.workout.text
import org.jetbrains.compose.resources.stringResource

private val SkeletonDashboardHeight = Spacing.xl * 9
private val SkeletonSectionHeight = Spacing.xl * 3
private const val SKELETON_SECTIONS = 4

/**
 * Stateless "Diario" screen (US-19, US-20) on `AppScreen` with the date switcher as its header: the defaults notice,
 * the progress dashboard on top and one card per meal below it. Wide windows put the dashboard
 * left of the meals. [onBack] (a past-day route) adds the back arrow to the header.
 */
@Composable
fun DiaryScreen(
    state: DiaryUiState,
    onEvent: (DiaryEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().testTag("diary_screen")) {
        when (state) {
            DiaryUiState.Loading -> PlainDiary(onBack) { DiaryLoading() }

            DiaryUiState.Error -> PlainDiary(onBack) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.diary_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(DiaryEvent.Retry) },
                        testTagPrefix = "diary_error",
                    )
                }
            }

            is DiaryUiState.Content -> DiaryDay(state.content, onEvent, onBack)
        }
    }
}

/** Loading and error have no date yet: the standard bar with the section title instead of the date switcher. */
@Composable
private fun PlainDiary(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    AppScreen(
        title = stringResource(Res.string.nav_diary),
        onBack = onBack,
        actions = listOfNotNull(if (onBack == null) notificationsBarAction("diary_action_notifications") else null),
        maxContentWidth = WindowWidthClassDefaults.CardColumnMaxWidth,
        testTagPrefix = "diary_bar",
    ) { content() }
}

@Composable
private fun DiaryDay(content: DiaryContent, onEvent: (DiaryEvent) -> Unit, onBack: (() -> Unit)?) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    if (pickerOpen) {
        DiaryDayPickerSheet(
            selected = content.date,
            today = content.today,
            isToday = content.isToday,
            markedDays = content.pickerMarkedDays,
            onPickDay = { onEvent(DiaryEvent.GoToDay(it)) },
            onGoToToday = { onEvent(DiaryEvent.GoToToday) },
            onMonthChange = { onEvent(DiaryEvent.PickerMonthShown(it)) },
            onDismiss = { pickerOpen = false },
        )
    }
    val actions = EntryActions(
        canDelete = !content.isOffline,
        onEdit = { onEvent(DiaryEvent.EditEntry(it)) },
        onDelete = { onEvent(DiaryEvent.DeleteEntry(it)) },
        expandedGroupIds = content.expandedGroupIds,
        onToggleGroup = { onEvent(DiaryEvent.ToggleGroup(it)) },
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        AppScreen(
            title = content.label.text(),
            header = {
                DiaryDateHeader(content.label, content.canGoNext, onEvent, { pickerOpen = true }, onBack = onBack)
            },
            maxContentWidth = if (wide) {
                WindowWidthClassDefaults.TwoColumnContentMaxWidth
            } else {
                WindowWidthClassDefaults.CardColumnMaxWidth
            },
            testTagPrefix = "diary",
        ) {
            if (wide) WideDay(content, actions, onEvent) else NarrowDay(content, actions, onEvent)
        }
    }
}

@Composable
private fun NarrowDay(content: DiaryContent, actions: EntryActions, onEvent: (DiaryEvent) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        if (content.defaultsFailed) item(key = "defaults") { DefaultsFailedMessage(onEvent) }
        item(key = "dashboard") { DiaryDashboard(content, onEvent) }
        mealItems(content, actions, onEvent)
    }
}

@Composable
private fun WideDay(content: DiaryContent, actions: EntryActions, onEvent: (DiaryEvent) -> Unit) {
    Row(Modifier.fillMaxSize().testTag("diary_two_column")) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            if (content.defaultsFailed) DefaultsFailedMessage(onEvent)
            DiaryDashboard(content, onEvent)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) { mealItems(content, actions, onEvent) }
    }
}

/** One lazy item per meal card, keyed by the slot so a reordered or collapsed meal animates in place. */
private fun LazyListScope.mealItems(content: DiaryContent, actions: EntryActions, onEvent: (DiaryEvent) -> Unit) {
    items(content.meals, key = { it.slot.id }) { section ->
        MealSectionCard(
            section = section,
            collapsed = section.slot.id in content.collapsedSlotIds,
            actions = actions,
            onToggle = { onEvent(DiaryEvent.ToggleSection(section.slot.id)) },
            onAdd = { onEvent(DiaryEvent.AddToMeal(section.slot.id)) },
            onSaveTemplate = { onEvent(DiaryEvent.SaveSection(section.slot.id)) },
            modifier = appAnimateItem(),
        )
    }
}

/** Non-blocking notice that today's default comidas could not be added; the Diary stays usable. */
@Composable
private fun DefaultsFailedMessage(onEvent: (DiaryEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.diary_defaults_failed),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f).testTag("diary_defaults_message"),
        )
        GhostButton(
            text = stringResource(Res.string.action_retry),
            onClick = { onEvent(DiaryEvent.RetryDefaults) },
            modifier = Modifier.testTag("diary_defaults_retry"),
        )
    }
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
