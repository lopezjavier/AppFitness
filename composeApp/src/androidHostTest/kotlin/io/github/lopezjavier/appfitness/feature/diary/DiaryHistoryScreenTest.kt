package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DiaryGoals
import io.github.lopezjavier.appfitness.domain.model.MealTotals
import io.github.lopezjavier.appfitness.domain.usecase.MonthSummary
import io.github.lopezjavier.appfitness.domain.usecase.PeriodAverage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 21)
private val Eighteenth = LocalDate(2026, 10, 18)
private val Goals = DiaryGoals(2400, 180, 270, 67)

private fun content(
    view: DiaryHistoryView = DiaryHistoryView.Day,
    selected: DiaryHistoryDay? = DiaryHistoryDay(
        Eighteenth,
        MealTotals(1840.0, 142.0, 210.0, 48.0),
        Goals,
    ),
    week: PeriodAverage? = PeriodAverage(3, 2000.0, 150.0, 220.0, 60.0),
    month: MonthSummary? = MonthSummary(20, 38000.0, 1900.0, 140.0, 200.0, 55.0),
    canGoNext: Boolean = false,
) = DiaryHistoryUiState.Content(
    DiaryHistoryContent(
        month = YearMonth(2026, 10),
        today = Today,
        canGoNext = canGoNext,
        markedDays = setOf(Eighteenth, LocalDate(2026, 10, 20)),
        view = view,
        selectedDay = selected,
        week = week,
        monthSummary = month,
        goals = Goals,
    ),
)

/** US-24 scenarios rendered by the stateless [DiaryHistoryScreen]. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class DiaryHistoryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<DiaryHistoryEvent>()

    private fun show(state: DiaryHistoryUiState) {
        rule.setContent { AppTheme { DiaryHistoryScreen(state, { events += it }) } }
        rule.waitForIdle()
    }

    private fun inside(tag: String, text: String) =
        rule.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun openingTheHistory_showsTheCalendarWithTheDaysWithEntriesMarked() {
        show(content())

        rule.onNodeWithTag("month_calendar_title").assertTextEquals("octubre 2026")
        rule.onNodeWithTag("month_calendar_day_2026-10-18").assertIsDisplayed()
        assertDescription("month_calendar_day_2026-10-18", "18 de octubre, con registros")
        assertDescription("month_calendar_day_2026-10-19", "19 de octubre, sin registros")
        rule.onNodeWithTag("month_calendar_day_2026-10-18").assertIsSelected()
    }

    @Test
    fun tappingADay_sendsSelectDay() {
        show(content())

        rule.onNodeWithTag("month_calendar_day_2026-10-20").performClick()

        assertEquals(listOf<DiaryHistoryEvent>(DiaryHistoryEvent.SelectDay(LocalDate(2026, 10, 20))), events)
    }

    @Test
    fun tappingTheSummaryRow_sendsOpenDay() {
        show(content())

        rule.onNodeWithTag("diary_history_row_open").performClick()

        assertEquals(listOf<DiaryHistoryEvent>(DiaryHistoryEvent.OpenDay), events)
    }

    @Test
    fun dailySummary_showsKcalAndMacrosAgainstTheTarget() {
        show(content())

        rule.onNode(hasText("1.840 kcal")).assertIsDisplayed()
        rule.onNode(hasText("de 2.400 kcal")).assertIsDisplayed()
        rule.onNodeWithTag("diary_history_macro_protein").assertIsDisplayed()
        rule.onNodeWithTag("diary_history_macro_carbs").assertIsDisplayed()
        rule.onNodeWithTag("diary_history_macro_fat").assertIsDisplayed()
    }

    @Test
    fun weeklyAverage_showsTheDailyAverage_withoutTheLoggedDaysLine() {
        show(content(view = DiaryHistoryView.Week))

        inside("diary_history_week_summary", "Media diaria").assertIsDisplayed()
        inside("diary_history_week_summary", "2.000 kcal").assertIsDisplayed()
        rule.onNode(hasText("días registrados", substring = true)).assertDoesNotExist()
    }

    @Test
    fun monthlySummary_showsTheDailyAverage_withoutTheTotalOrTheLoggedDays() {
        show(content(view = DiaryHistoryView.Month))

        inside("diary_history_month_summary", "Media diaria").assertIsDisplayed()
        inside("diary_history_month_summary", "1.900 kcal").assertIsDisplayed()
        rule.onNode(hasText("Total del mes")).assertDoesNotExist()
        rule.onNode(hasText("Días registrados")).assertDoesNotExist()
    }

    @Test
    fun dayWithoutEntries_showsSinRegistros_andStillOpensTheDay() {
        show(content(selected = DiaryHistoryDay(LocalDate(2026, 10, 19), null, Goals)))

        rule.onNodeWithTag("diary_history_empty", useUnmergedTree = true).assertTextEquals("Sin registros")
        rule.onNodeWithTag("diary_history_row_open").performClick()
        assertEquals(listOf<DiaryHistoryEvent>(DiaryHistoryEvent.OpenDay), events)
    }

    @Test
    fun weekAndMonthWithoutData_showSinRegistros() {
        show(content(view = DiaryHistoryView.Week, week = null))
        rule.onNodeWithTag("diary_history_empty", useUnmergedTree = true).assertTextEquals("Sin registros")
    }

    @Test
    fun monthWithoutData_showsSinRegistros() {
        show(content(view = DiaryHistoryView.Month, month = null))

        rule.onNodeWithTag("diary_history_empty", useUnmergedTree = true).assertTextEquals("Sin registros")
    }

    @Test
    fun viewSelector_sendsTheChosenView() {
        show(content())

        rule.onNodeWithTag("diary_history_view_week").performClick()
        rule.onNodeWithTag("diary_history_view_month").performClick()

        assertEquals(
            listOf<DiaryHistoryEvent>(
                DiaryHistoryEvent.SelectView(DiaryHistoryView.Week),
                DiaryHistoryEvent.SelectView(DiaryHistoryView.Month),
            ),
            events,
        )
    }

    @Test
    fun monthArrows_sendEvents_andNextIsDisabledAtTheCurrentMonth() {
        show(content(canGoNext = false))

        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.onNodeWithTag("month_calendar_next").assertIsNotEnabled()

        assertEquals(listOf<DiaryHistoryEvent>(DiaryHistoryEvent.PreviousMonth), events)
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(DiaryHistoryUiState.Loading)

        rule.onNodeWithTag("diary_history_loading").assertIsDisplayed()
    }

    @Test
    fun error_offersRetry() {
        show(DiaryHistoryUiState.Error)

        rule.onNodeWithTag("diary_history_error_action").performClick()

        assertEquals(listOf<DiaryHistoryEvent>(DiaryHistoryEvent.Retry), events)
    }

    private fun assertDescription(tag: String, expected: String) {
        val description = rule.onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf(expected), description)
    }
}
