package io.github.lopezjavier.appfitness.gallery

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.lopezjavier.appfitness.feature.workout.DayCardsState
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailScreen
import io.github.lopezjavier.appfitness.feature.workout.SessionDetailUiState
import io.github.lopezjavier.appfitness.feature.workout.WorkoutCalendarScreen
import io.github.lopezjavier.appfitness.feature.workout.WorkoutCalendarUiState
import io.github.lopezjavier.appfitness.feature.workout.WorkoutCalendarView
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val HISTORY_SCREEN = "Historial"
private const val SESSION_SCREEN = "Detalle de sesión"

/** The training calendar (HistoryRoute) and the detail of one finished session (SessionDetailRoute). */
@Suppress("LongMethod") // A list of fixtures.
private fun historyEntries(): List<GalleryEntry> {
    val fixtures = EntrenoFixtures
    val month = WorkoutCalendarUiState.Content(
        view = WorkoutCalendarView.Month,
        month = fixtures.calendarMonth,
        year = 2026,
        markedDays = fixtures.calendarMarked,
        dayCounts = mapOf(LocalDate(2026, 10, 19) to 1, LocalDate(2026, 10, 20) to 2),
        selectedDay = null,
        yearCounts = emptyMap(),
        monthCounts = emptyMap(),
        canGoNext = false,
        today = GalleryFixtures.today,
    )

    fun calendar(state: String, ui: WorkoutCalendarUiState, wide: Boolean = false) =
        GalleryEntry(ENTRENO_AREA, HISTORY_SCREEN, state, covers = setOf("HistoryRoute"), wide = wide) {
            WorkoutCalendarScreen(ui, onEvent = {})
        }

    val selected = month.copy(
        selectedDay = LocalDate(2026, 10, 20),
        dayCards = DayCardsState.Loaded(fixtures.dayRows),
        canAddToSelectedDay = true,
    )
    val calendarEntries = listOf(
        calendar("loading", WorkoutCalendarUiState.Loading),
        calendar("load failed", WorkoutCalendarUiState.Error),
        calendar("month with workouts", month),
        calendar("empty month", month.copy(markedDays = emptySet(), dayCounts = emptyMap())),
        calendar("selected day with several workouts", selected),
        calendar("selected day cards loading", selected.copy(dayCards = DayCardsState.Loading)),
        calendar("selected day cards failed", selected.copy(dayCards = DayCardsState.Error)),
        calendar("year view", month.copy(view = WorkoutCalendarView.Year, monthCounts = mapOf(10 to 3))),
        calendar(
            "multi-year view",
            month.copy(view = WorkoutCalendarView.MultiYear, yearCounts = mapOf(2025 to 40, 2026 to 12)),
        ),
        calendar("tablet width", selected, wide = true),
    )

    fun session(
        state: String,
        ui: SessionDetailUiState,
        kind: GalleryKind = GalleryKind.Screen,
        wide: Boolean = false,
        vararg covers: String,
    ) = GalleryEntry(
        ENTRENO_AREA,
        SESSION_SCREEN,
        state,
        kind,
        covers = setOf("SessionDetailRoute") + covers,
        wide = wide,
    ) { SessionDetailScreen(ui, onEvent = {}) }

    val data = SessionDetailUiState.Content(
        name = "Torso A",
        date = GalleryFixtures.today,
        today = GalleryFixtures.today,
        stats = fixtures.sessionStats,
        exercises = fixtures.sessionExercises,
        split = fixtures.sessionSplit,
    )
    val sessionEntries = listOf(
        session("loading", SessionDetailUiState.Loading),
        session("load failed", SessionDetailUiState.Error),
        session("data with records and split", data),
        session("offline", data.copy(isOnline = false)),
        session("busy", data.copy(busy = true)),
        session(
            "save as routine sheet",
            data.copy(saveAsRoutine = fixtures.saveAsRoutine),
            GalleryKind.Sheet,
            covers = arrayOf("SaveAsRoutineSheet"),
        ),
        session("tablet width", data, wide = true),
    )
    return calendarEntries + sessionEntries
}

internal fun entrenoHistoryEntries(): List<GalleryEntry> = historyEntries()

/** Renders the Entreno history entries (US-131); skipped unless `-Pgallery`. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class EntrenoHistoryGallery {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersHistory() = rule.renderGallery(entrenoHistoryEntries().filter { !it.wide && !it.tall })
}
