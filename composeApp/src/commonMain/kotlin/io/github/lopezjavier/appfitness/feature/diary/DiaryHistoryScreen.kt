package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_title
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_view_day
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_view_month
import io.github.lopezjavier.appfitness.composeapp.resources.diary_history_view_week
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import org.jetbrains.compose.resources.stringResource

private val SkeletonCalendarHeight = Spacing.xl * 11
private val SkeletonRowHeight = Spacing.xl * 6

/**
 * Stateless diary history (US-24, glossary "Calorías"): the Día / Semana / Mes selector, the month calendar and the
 * summary of the chosen panel on an `AppScreen` with a back arrow. Wide windows put the calendar and the summary side
 * by side.
 */
@Composable
fun DiaryHistoryScreen(
    state: DiaryHistoryUiState,
    onEvent: (DiaryHistoryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        AppScreen(
            title = stringResource(Res.string.diary_history_title),
            onBack = onBack,
            maxContentWidth = if (wide) {
                WindowWidthClassDefaults.TwoColumnContentMaxWidth
            } else {
                WindowWidthClassDefaults.CardColumnMaxWidth
            },
            testTagPrefix = "diary_history",
        ) {
            Column(Modifier.fillMaxSize().testTag("diary_history_screen")) {
                when (state) {
                    DiaryHistoryUiState.Loading -> HistoryLoading()

                    DiaryHistoryUiState.Error -> ErrorState(
                        message = stringResource(Res.string.diary_history_error_message),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(DiaryHistoryEvent.Retry)
                        },
                        testTagPrefix = "diary_history_error",
                    )

                    is DiaryHistoryUiState.Content -> HistoryContent(state.content, wide, onEvent)
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(content: DiaryHistoryContent, wide: Boolean, onEvent: (DiaryHistoryEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        ViewSelector(content.view, onEvent)
        if (wide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                Calendar(content, onEvent, Modifier.weight(1f))
                DiaryHistorySummary(content, onEvent, Modifier.weight(1f))
            }
        } else {
            Calendar(content, onEvent)
            DiaryHistorySummary(content, onEvent)
        }
    }
}

@Composable
private fun ViewSelector(view: DiaryHistoryView, onEvent: (DiaryHistoryEvent) -> Unit) {
    SegmentedSelector(
        options = listOf(
            AppOption(stringResource(Res.string.diary_history_view_day), "diary_history_view_day"),
            AppOption(stringResource(Res.string.diary_history_view_week), "diary_history_view_week"),
            AppOption(stringResource(Res.string.diary_history_view_month), "diary_history_view_month"),
        ),
        selectedIndex = view.ordinal,
        onSelect = { onEvent(DiaryHistoryEvent.SelectView(DiaryHistoryView.entries[it])) },
    )
}

@Composable
private fun Calendar(
    content: DiaryHistoryContent,
    onEvent: (DiaryHistoryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MonthCalendarGrid(
        month = content.month,
        markedDays = content.markedDays,
        selected = content.selectedDay?.date,
        today = content.today,
        onSelect = { onEvent(DiaryHistoryEvent.SelectDay(it)) },
        onPrevMonth = { onEvent(DiaryHistoryEvent.PreviousMonth) },
        onNextMonth = { onEvent(DiaryHistoryEvent.NextMonth) },
        canGoNext = content.canGoNext,
        modifier = modifier,
    )
}

@Composable
private fun HistoryLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("diary_history_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCalendarHeight), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRowHeight), AppShapes.lg)
    }
}
