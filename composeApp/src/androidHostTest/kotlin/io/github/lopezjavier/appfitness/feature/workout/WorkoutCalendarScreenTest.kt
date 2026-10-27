package io.github.lopezjavier.appfitness.feature.workout

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 19)
private val TrainedDay = LocalDate(2026, 10, 17)

private fun card(id: String, name: String) = HistoryRow(
    id = id,
    label = DateLabel.Other(DayOfWeek.SATURDAY, 17, Month.OCTOBER),
    name = name,
    durationSeconds = 3_725,
    volumeKg = 2_120.0,
    completedSets = 3,
)

private fun content(
    view: WorkoutCalendarView = WorkoutCalendarView.Month,
    dayCards: DayCardsState = DayCardsState.None,
    selectedDay: LocalDate? = null,
    canAddToSelectedDay: Boolean = false,
) = WorkoutCalendarUiState.Content(
    view = view,
    month = YearMonth(2026, 10),
    year = 2026,
    markedDays = setOf(TrainedDay),
    dayCounts = mapOf(TrainedDay to 1),
    selectedDay = selectedDay,
    yearCounts = mapOf(2026 to 7, 2024 to 3),
    monthCounts = mapOf(3 to 5, 10 to 2),
    canGoNext = false,
    today = Today,
    dayCards = dayCards,
    canAddToSelectedDay = canAddToSelectedDay,
)

/** US-37 training calendar: trained days in month, year and multi-year views, and opening a workout. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per Gherkin scenario.
class WorkoutCalendarScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<WorkoutCalendarEvent>()

    private fun show(state: WorkoutCalendarUiState) {
        rule.setContent { AppTheme { WorkoutCalendarScreen(state, { events += it }) } }
        rule.waitForIdle()
    }

    @Test
    fun monthView_marksTheTrainedDay_andTappingItSelectsIt() {
        show(content())

        rule.onNodeWithTag("calendar_view_month").assertIsSelected()
        rule.onNodeWithTag(
            "month_calendar_day_$TrainedDay",
        ).assert(hasContentDescription("17 de octubre, con registros"))
        rule.onNodeWithTag("month_calendar_day_$TrainedDay").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.SelectDay(TrainedDay)), events)
    }

    @Test
    fun monthView_navigatesBetweenMonths() {
        show(content())

        rule.onNodeWithTag("month_calendar_prev").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.PreviousMonth), events)
    }

    @Test
    fun viewSelector_switchesBetweenMonthYearAndYears() {
        show(content())

        rule.onNodeWithTag("calendar_view_year").performClick()
        rule.onNodeWithTag("calendar_view_years").performClick()

        assertEquals(
            listOf<WorkoutCalendarEvent>(
                WorkoutCalendarEvent.SelectView(WorkoutCalendarView.Year),
                WorkoutCalendarEvent.SelectView(WorkoutCalendarView.MultiYear),
            ),
            events,
        )
    }

    @Test
    fun yearView_showsTheWorkoutCountOfEachMonth_andTappingOneOpensIt() {
        show(content(WorkoutCalendarView.Year))

        rule.onNodeWithTag("calendar_year_month_3").assert(hasContentDescription("Marzo, 5 entrenos"))
        rule.onNodeWithTag("calendar_year_month_10").assert(hasContentDescription("Octubre, 2 entrenos"))
        rule.onNodeWithTag("calendar_year_month_1").assert(hasContentDescription("Enero, 0 entrenos"))
        rule.onNodeWithTag("calendar_year_month_3").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.SelectMonth(YearMonth(2026, 3))), events)
    }

    @Test
    fun multiYearView_listsTheYearsWithTheirCounts_andTappingOneOpensIt() {
        show(content(WorkoutCalendarView.MultiYear))

        rule.onNodeWithTag("calendar_years_year_2026").assert(hasContentDescription("2026, 7 entrenos"))
        rule.onNodeWithTag("calendar_years_year_2024").assert(hasContentDescription("2024, 3 entrenos"))
        rule.onNodeWithTag("calendar_years_year_2024").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.SelectYear(2024)), events)
    }

    @Test
    fun loading_showsASkeleton() {
        show(WorkoutCalendarUiState.Loading)

        rule.onNodeWithTag("calendar_loading").assertExists()
    }

    @Test
    fun withoutAnyWorkout_theCalendarStillShows_andAPastDayCanBePicked() {
        show(content().copy(markedDays = emptySet(), dayCounts = emptyMap(), yearCounts = emptyMap()))

        rule.onNodeWithTag("month_calendar_day_2026-10-12").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.SelectDay(LocalDate(2026, 10, 12))), events)
    }

    @Test
    fun theScreen_isTitledHistorialDeEntrenos_withABackArrow() {
        var back = 0
        rule.setContent { AppTheme { WorkoutCalendarScreen(content(), {}, onBack = { back++ }) } }

        rule.onNodeWithTag("history_screen").assertExists()
        rule.onNodeWithTag("history_title").assertTextEquals("Historial de entrenos")
        rule.onNodeWithTag("history_back").performClick()

        assertEquals(1, back)
    }

    @Test
    fun theHistorialHasNoListNoPlusAndNoCalendarButton() {
        show(content())

        rule.onNodeWithTag("history_list").assertDoesNotExist()
        rule.onNodeWithTag("history_add_past_workout").assertDoesNotExist()
        rule.onNodeWithTag("history_calendar").assertDoesNotExist()
    }

    @Test
    fun aSelectedDay_showsItsDateHeading_itsWorkouts_andAnAddButton() {
        show(
            content(
                dayCards = DayCardsState.Loaded(listOf(card("s1", "Torso"))),
                selectedDay = TrainedDay,
                canAddToSelectedDay = true,
            ),
        )

        rule.onNodeWithTag("calendar_day_heading").assertTextEquals("17 de octubre")
        rule.onNodeWithTag("calendar_day_card_s1").assertIsDisplayed()
        rule.onNodeWithTag("calendar_add_workout").assertTextContains("Añadir entreno")
        rule.onNodeWithTag("calendar_add_workout").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.AddWorkout), events)
    }

    @Test
    fun aDayWithoutWorkouts_saysSinEntrenos_andStillOffersTheAddButton() {
        show(
            content(
                dayCards = DayCardsState.Loaded(emptyList()),
                selectedDay = LocalDate(2026, 10, 12),
                canAddToSelectedDay = true,
            ),
        )

        rule.onNodeWithTag("calendar_day_empty").assertTextEquals("Sin entrenos")
        rule.onNodeWithTag("calendar_add_workout").assertExists()
    }

    @Test
    fun aDayThatCannotBeAddedTo_hidesTheAddButton() {
        show(
            content(
                dayCards = DayCardsState.Loaded(emptyList()),
                selectedDay = Today,
                canAddToSelectedDay = false,
            ),
        )

        rule.onNodeWithTag("calendar_add_workout").assertDoesNotExist()
    }

    @Test
    fun error_offersRetry() {
        show(WorkoutCalendarUiState.Error)

        rule.onNodeWithTag("calendar_error_action").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.Retry), events)
    }

    @Test
    fun selectedDayWithOneWorkout_showsItsCard_andOnlyTheCardTapOpensIt() {
        show(content(dayCards = DayCardsState.Loaded(listOf(card("s1", "Torso"))), selectedDay = TrainedDay))

        rule.onNodeWithTag("calendar_day_cards").assertExists()
        rule.onNodeWithTag("calendar_day_card_s1").assertIsDisplayed()
        assertEquals(emptyList<WorkoutCalendarEvent>(), events)
        rule.onNodeWithTag("calendar_day_card_s1").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.OpenSession("s1")), events)
    }

    @Test
    fun selectedDayWithTwoWorkouts_showsBothCardsWithNameDurationAndVolume() {
        show(
            content(
                dayCards = DayCardsState.Loaded(listOf(card("s1", "Torso"), card("s2", "Pierna"))),
                selectedDay = TrainedDay,
            ),
        )

        val summary = "duración 1:02:05, volumen 2.120 kg"
        rule.onNodeWithTag("calendar_day_card_s1").assert(hasContentDescription(summary, substring = true))
        rule.onNodeWithTag("calendar_day_card_s2").assert(hasContentDescription("Pierna", substring = true))
        rule.onNodeWithTag("calendar_day_card_s2").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.OpenSession("s2")), events)
    }

    @Test
    fun noSelectedDay_showsNoCardsAndNoChoicesSheet() {
        show(content())

        rule.onNodeWithTag("calendar_day_cards").assertDoesNotExist()
        rule.onNodeWithTag("calendar_choices_sheet").assertDoesNotExist()
    }

    @Test
    fun loadingCards_showsASkeleton() {
        show(content(dayCards = DayCardsState.Loading, selectedDay = TrainedDay))

        rule.onNodeWithTag("calendar_day_cards_loading").assertExists()
    }

    @Test
    fun failedCards_offerRetry() {
        show(content(dayCards = DayCardsState.Error, selectedDay = TrainedDay))

        rule.onNodeWithTag("calendar_day_cards_error_action").performClick()

        assertEquals(listOf<WorkoutCalendarEvent>(WorkoutCalendarEvent.RetryDayCards), events)
    }
}
