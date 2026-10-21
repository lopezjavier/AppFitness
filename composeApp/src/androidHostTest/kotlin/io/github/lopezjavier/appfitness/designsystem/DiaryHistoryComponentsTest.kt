package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val October = YearMonth(2026, 10)
private val Today = LocalDate(2026, 10, 21)

/** [MonthCalendarGrid] and [DiaryHistoryRow] (T-062). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per behaviour.
class DiaryHistoryComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun calendar(
        marked: Set<LocalDate> = setOf(LocalDate(2026, 10, 18)),
        selected: LocalDate? = null,
        canGoNext: Boolean = true,
        onSelect: (LocalDate) -> Unit = {},
        onPrev: () -> Unit = {},
        onNext: () -> Unit = {},
    ) = rule.setContent {
        AppTheme { MonthCalendarGrid(October, marked, selected, Today, onSelect, onPrev, onNext, canGoNext) }
    }

    @Test
    fun weeksOf_startsOnMonday_andPadsTheEdges() {
        val weeks = weeksOf(October)

        // 1 Oct 2026 is a Thursday: three leading blanks; 31 days end on a Saturday.
        assertEquals(5, weeks.size)
        assertEquals(listOf(null, null, null, LocalDate(2026, 10, 1)), weeks.first().take(4))
        assertEquals(LocalDate(2026, 10, 31), weeks.last()[5])
        assertEquals(null, weeks.last()[6])
    }

    @Test
    fun title_showsTheMonthAndYear() {
        calendar()

        rule.onNodeWithTag("month_calendar_title").assertTextEquals("octubre 2026")
    }

    @Test
    fun markedDay_isDescribedAsHavingEntries_andUnmarkedAsEmpty() {
        calendar()

        assertEquals("18 de octubre, con registros", description("month_calendar_day_2026-10-18"))
        assertEquals("19 de octubre, sin registros", description("month_calendar_day_2026-10-19"))
    }

    @Test
    fun tappingADay_reportsItsDate_evenWithoutEntries() {
        val picked = mutableListOf<LocalDate>()
        calendar(onSelect = { picked += it })

        rule.onNodeWithTag("month_calendar_day_2026-10-19").performClick()

        assertEquals(listOf(LocalDate(2026, 10, 19)), picked)
    }

    @Test
    fun selectedDay_isSelected() {
        calendar(selected = LocalDate(2026, 10, 18))

        rule.onNodeWithTag("month_calendar_day_2026-10-18").assertIsSelected()
    }

    @Test
    fun daysAfterToday_areInert() {
        calendar()

        rule.onNodeWithTag("month_calendar_day_2026-10-22").assertIsNotEnabled()
        rule.onNodeWithTag("month_calendar_day_2026-10-21").assertIsEnabled()
    }

    @Test
    fun nextArrow_isDisabledWhenCannotGoNext() {
        var next = 0
        calendar(canGoNext = false, onNext = { next++ })

        rule.onNodeWithTag("month_calendar_next").assertIsNotEnabled().performClick()

        assertEquals(0, next)
    }

    @Test
    fun arrows_reportMonthChanges() {
        var prev = 0
        var next = 0
        calendar(onPrev = { prev++ }, onNext = { next++ })

        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.onNodeWithTag("month_calendar_next").performClick()

        assertEquals(1, prev)
        assertEquals(1, next)
    }

    @Test
    fun row_showsRoundedKcalAndMacrosAgainstTheTargets() {
        rule.setContent {
            AppTheme {
                DiaryHistoryRow(
                    "18 oct",
                    HistoryTotals(1839.6, 142.0, 210.0, 48.0),
                    HistoryTargets(2400, 180, 270, 67),
                    "Sin registros",
                )
            }
        }

        rule.onNode(hasText("1.840 kcal")).assertExists()
        rule.onNode(hasText("de 2.400 kcal")).assertExists()
        rule.onNodeWithTag("diary_history_macro_protein").assertExists()
        rule.onNodeWithTag("diary_history_row").assertExists()
    }

    @Test
    fun row_withoutTotals_showsTheEmptyText() {
        rule.setContent { AppTheme { DiaryHistoryRow("18 oct", null, null, "Sin registros") } }

        rule.onNodeWithTag("diary_history_empty").assertTextEquals("Sin registros")
    }

    @Test
    fun row_withoutTargets_showsPlainMacros() {
        rule.setContent {
            AppTheme { DiaryHistoryRow("18 oct", HistoryTotals(1840.0, 142.0, 210.0, 48.0), null, "Sin registros") }
        }

        rule.onNode(hasText("P 142 g")).assertExists()
        rule.onNodeWithTag("diary_history_macro_protein").assertDoesNotExist()
    }

    @Test
    fun tappableRow_reportsTheClick() {
        var clicks = 0
        rule.setContent {
            AppTheme { DiaryHistoryRow("18 oct", null, null, "Sin registros", onClick = { clicks++ }) }
        }

        rule.onNodeWithTag("diary_history_row_open").performClick()

        assertTrue(clicks == 1)
    }

    private fun description(tag: String): String? =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
}
