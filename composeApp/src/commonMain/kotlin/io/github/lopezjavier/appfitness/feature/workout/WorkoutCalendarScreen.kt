package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_error
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_view_month
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_view_year
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_view_years
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.SegmentedSelector
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SkeletonCalendarHeight = Spacing.xl * 11

/** Stateful training calendar: connects [WorkoutCalendarViewModel] and opens a workout through [onOpenSession]. */
@Composable
fun WorkoutCalendarRoot(
    onOpenSession: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutCalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOpen by rememberUpdatedState(onOpenSession)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WorkoutCalendarEffect.OpenSession -> currentOpen(effect.sessionId)
            }
        }
    }
    WorkoutCalendarScreen(state, viewModel::onEvent, modifier)
}

/**
 * Training calendar (US-37): a Mes / Año / Años selector over the month grid, the 12 month tiles or the list of years.
 * Trained days carry a dot; a tap opens the workout of that day (or the picker when there are two). No streaks.
 */
@Composable
fun WorkoutCalendarScreen(
    state: WorkoutCalendarUiState,
    onEvent: (WorkoutCalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    Box(modifier.fillMaxSize().testTag("calendar_screen"), contentAlignment = Alignment.TopCenter) {
        when (state) {
            WorkoutCalendarUiState.Loading -> CalendarLoading()

            WorkoutCalendarUiState.Error -> Centered {
                ErrorState(
                    message = stringResource(Res.string.calendar_error),
                    action = AppStateAction(stringResource(Res.string.state_retry)) {
                        onEvent(WorkoutCalendarEvent.Retry)
                    },
                    testTagPrefix = "calendar_error",
                )
            }

            WorkoutCalendarUiState.Empty -> Centered {
                EmptyState(
                    message = stringResource(Res.string.calendar_empty_message),
                    title = stringResource(Res.string.calendar_empty_title),
                    testTagPrefix = "calendar_empty",
                )
            }

            is WorkoutCalendarUiState.Content -> CalendarContent(state, onEvent, zone)
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CalendarContent(
    state: WorkoutCalendarUiState.Content,
    onEvent: (WorkoutCalendarEvent) -> Unit,
    zone: TimeZone,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
        Column(
            Modifier
                .widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            ViewSelector(state.view, onEvent)
            when (state.view) {
                WorkoutCalendarView.Month -> MonthCalendarGrid(
                    month = state.month,
                    markedDays = state.markedDays,
                    selected = state.selectedDay,
                    today = state.today,
                    onSelect = { onEvent(WorkoutCalendarEvent.SelectDay(it)) },
                    onPrevMonth = { onEvent(WorkoutCalendarEvent.PreviousMonth) },
                    onNextMonth = { onEvent(WorkoutCalendarEvent.NextMonth) },
                    canGoNext = state.canGoNext,
                )

                WorkoutCalendarView.Year -> YearMonthTiles(
                    year = state.year,
                    monthCounts = state.monthCounts,
                    wide = wide,
                    onSelectMonth = { onEvent(WorkoutCalendarEvent.SelectMonth(YearMonth(state.year, it))) },
                )

                WorkoutCalendarView.MultiYear -> YearRows(
                    yearCounts = state.yearCounts,
                    onSelectYear = { onEvent(WorkoutCalendarEvent.SelectYear(it)) },
                )
            }
        }
    }
    if (state.dayChoices.isNotEmpty()) {
        WorkoutChoicesSheet(
            sessions = state.dayChoices,
            zone = zone,
            onPick = { onEvent(WorkoutCalendarEvent.PickSession(it)) },
            onDismiss = { onEvent(WorkoutCalendarEvent.DismissChoices) },
        )
    }
}

@Composable
private fun ViewSelector(view: WorkoutCalendarView, onEvent: (WorkoutCalendarEvent) -> Unit) {
    SegmentedSelector(
        options = listOf(
            AppOption(stringResource(Res.string.calendar_view_month), "calendar_view_month"),
            AppOption(stringResource(Res.string.calendar_view_year), "calendar_view_year"),
            AppOption(stringResource(Res.string.calendar_view_years), "calendar_view_years"),
        ),
        selectedIndex = view.ordinal,
        onSelect = { onEvent(WorkoutCalendarEvent.SelectView(WorkoutCalendarView.entries[it])) },
    )
}

@Composable
private fun CalendarLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("calendar_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCalendarHeight), AppShapes.lg)
    }
}
