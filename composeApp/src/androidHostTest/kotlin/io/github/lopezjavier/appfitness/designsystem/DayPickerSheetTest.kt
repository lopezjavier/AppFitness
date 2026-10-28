package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 21)

/** US-56 "Calendario": the day picker on the picker archetype, with marked days and the "Hoy" chip. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class DayPickerSheetTest {
    @get:Rule
    val rule = createComposeRule()

    private val picked = mutableListOf<LocalDate>()
    private val months = mutableListOf<YearMonth>()
    private var goneToToday = 0
    private var dismissed = 0
    private var open by mutableStateOf(true)

    private fun show(selected: LocalDate = Today, marked: Set<LocalDate> = emptySet()) {
        rule.setContent { AppTheme { Picker(selected, marked) } }
        rule.waitForIdle()
    }

    @Composable
    private fun Picker(selected: LocalDate, marked: Set<LocalDate> = emptySet()) {
        if (open) {
            DayPickerSheet(
                title = "Calendario",
                todayContentDescription = "Ir a hoy",
                tagPrefix = "diary_day_picker",
                selected = selected,
                today = Today,
                isToday = selected == Today,
                markedDays = marked,
                onPickDay = { picked += it },
                onGoToToday = { goneToToday++ },
                onMonthChange = { months += it },
                onDismiss = {
                    dismissed++
                    open = false
                },
            )
        }
    }

    private fun description(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single()

    @Test
    fun theTitleIsCalendario() {
        show()

        rule.onNodeWithText("Calendario").assertExists()
    }

    @Test
    fun markedDays_carryTheDot() {
        show(marked = setOf(LocalDate(2026, 10, 18)))

        assertEquals("18 de octubre, con registros", description("month_calendar_day_2026-10-18"))
        assertEquals("19 de octubre, sin registros", description("month_calendar_day_2026-10-19"))
    }

    @Test
    fun hoyChip_isAbsentOnToday() {
        show(selected = Today)

        rule.onNodeWithTag("diary_day_picker_today").assertDoesNotExist()
    }

    @Test
    fun hoyChip_onAnotherDay_goesToTodayAndCloses() {
        show(selected = LocalDate(2026, 10, 12))

        assertEquals("Ir a hoy", description("diary_day_picker_today"))
        rule.onNodeWithTag("diary_day_picker_today").performClick()
        rule.waitForIdle()

        assertEquals(1, goneToToday)
        assertEquals(1, dismissed)
    }

    @Test
    fun pickingADay_appliesOnTapAndCloses() {
        show()

        rule.onNodeWithTag("month_calendar_day_2026-10-12").performClick()
        rule.waitForIdle()

        assertEquals(listOf(LocalDate(2026, 10, 12)), picked)
        assertEquals(1, dismissed)
    }

    @Test
    fun theShownMonthIsReported_includingTheFirstOne() {
        show()

        assertEquals(YearMonth(2026, 10), months.first())
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()
        assertEquals(YearMonth(2026, 9), months.last())
    }

    @Test
    fun theShownMonth_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { AppTheme { Picker(Today) } }
        rule.waitForIdle()
        rule.onNodeWithTag("month_calendar_prev").performClick()
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("month_calendar_title").assertTextEquals("septiembre 2026")
    }
}
