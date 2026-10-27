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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_error
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_view_month
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_view_year
import io.github.lopezjavier.appfitness.composeapp.resources.calendar_view_years
import io.github.lopezjavier.appfitness.composeapp.resources.history_title
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SkeletonCalendarHeight = Spacing.xl * 11

/**
 * Stateful Historial (the training calendar): connects [WorkoutCalendarViewModel], reloads when the screen comes
 * back (a workout saved, edited or deleted elsewhere), opens a workout through [onOpenSession] and hosts the source
 * chooser that "Añadir entreno" opens for the selected day; its pick starts the past-workout form via
 * [onStartPastWorkout].
 */
@Composable
fun WorkoutCalendarRoot(
    onOpenSession: (sessionId: String) -> Unit,
    onBack: () -> Unit,
    onStartPastWorkout: (PastWorkoutSource, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutCalendarViewModel = koinViewModel(),
    chooser: PastWorkoutChooserViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chooserState by chooser.uiState.collectAsStateWithLifecycle()
    val currentOpen by rememberUpdatedState(onOpenSession)
    val currentStart by rememberUpdatedState(onStartPastWorkout)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WorkoutCalendarEffect.OpenSession -> currentOpen(effect.sessionId)
                is WorkoutCalendarEffect.AddWorkout -> chooser.onEvent(PastWorkoutChooserEvent.Open(effect.date))
            }
        }
    }
    LaunchedEffect(chooser) {
        chooser.effects.collect { effect ->
            when (effect) {
                is PastWorkoutChooserEffect.Start -> currentStart(effect.source, effect.date)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(WorkoutCalendarEvent.Refresh) }
    WorkoutCalendarScreen(state, viewModel::onEvent, modifier, onBack)
    PastWorkoutChooserSheet(chooserState, chooser::onEvent)
}

/**
 * Historial de entrenos (US-37): a Mes / Año / Años selector over the month grid, the 12 month tiles or the list of
 * years. Trained days carry a dot; a tap on a day up to today selects it and shows its workouts below the grid, with
 * "Añadir entreno" to log one on that day. No streaks.
 */
@Composable
fun WorkoutCalendarScreen(
    state: WorkoutCalendarUiState,
    onEvent: (WorkoutCalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    AppScreen(
        title = stringResource(Res.string.history_title),
        modifier = modifier,
        onBack = onBack,
        testTagPrefix = "history",
    ) {
        Box(Modifier.fillMaxSize().testTag("history_screen"), contentAlignment = Alignment.TopCenter) {
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

                is WorkoutCalendarUiState.Content -> CalendarContent(state, onEvent)
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CalendarContent(state: WorkoutCalendarUiState.Content, onEvent: (WorkoutCalendarEvent) -> Unit) {
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
                WorkoutCalendarView.Month -> {
                    MonthCalendarGrid(
                        month = state.month,
                        markedDays = state.markedDays,
                        selected = state.selectedDay,
                        today = state.today,
                        onSelect = { onEvent(WorkoutCalendarEvent.SelectDay(it)) },
                        onPrevMonth = { onEvent(WorkoutCalendarEvent.PreviousMonth) },
                        onNextMonth = { onEvent(WorkoutCalendarEvent.NextMonth) },
                        canGoNext = state.canGoNext,
                    )
                    state.selectedDay?.let {
                        CalendarDaySection(it, state.dayCards, state.canAddToSelectedDay, onEvent)
                    }
                }

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
