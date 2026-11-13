package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R-64 month calendar visuals (selected fill and lime border, today ring, descriptions). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class MonthCalendarVisualsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun monthCalendarGrid_selectedDay_hasLimeBorderAndSelectedContainerFill() {
        var selectedContainer = Color.Unspecified
        var lime = Color.Unspecified
        var base = Color.Unspecified
        rule.setContent {
            AppTheme {
                selectedContainer = AppTheme.colors.selectedContainer
                lime = AppTheme.colors.neonLime
                base = AppTheme.colors.surfaceBase
                MonthCalendarGrid(
                    month = YearMonth(2026, 10),
                    markedDays = emptySet(),
                    selected = LocalDate(2026, 10, 14),
                    today = LocalDate(2026, 10, 20),
                    onSelect = {},
                    onPrevMonth = {},
                    onNextMonth = {},
                    canGoNext = true,
                    modifier = Modifier.testTag("grid"),
                )
            }
        }
        rule.waitForIdle()

        val selected = rule.onNodeWithTag("month_calendar_day_2026-10-14")
        val other = rule.onNodeWithTag("month_calendar_day_2026-10-15")
        val selectedMap = selected.captureToImage().toPixelMap()
        val otherMap = other.captureToImage().toPixelMap()
        val x = selectedMap.width / 2
        // 48 dp cell, 4 dp padding, 1 dp border: the ring is at 4..5 dp, the fill just below it (xxhdpi = 3 px/dp).
        assertEquals(lime, selectedMap[x, 14].withoutAlpha())
        assertEquals(selectedContainer, selectedMap[x, 24].withoutAlpha())
        assertEquals(base, otherMap[x, 14].withoutAlpha())
        assertEquals(base, otherMap[x, 24].withoutAlpha())
    }

    private fun showCalendar(selected: LocalDate?, today: LocalDate) {
        rule.setContent {
            AppTheme {
                MonthCalendarGrid(
                    month = YearMonth(2026, 10),
                    markedDays = emptySet(),
                    selected = selected,
                    today = today,
                    onSelect = {},
                    onPrevMonth = {},
                    onNextMonth = {},
                    canGoNext = true,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun description(date: String): String? = rule.onNodeWithTag("month_calendar_day_$date")
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test
    fun monthCalendarGrid_todayNotSelected_hasAThinCyanRing() {
        var cyan = Color.Unspecified
        rule.setContent {
            AppTheme {
                cyan = AppTheme.colors.athleticCyan
                MonthCalendarGrid(YearMonth(2026, 10), emptySet(), null, LocalDate(2026, 10, 14), {}, {}, {}, true)
            }
        }
        rule.waitForIdle()

        val map = rule.onNodeWithTag("month_calendar_day_2026-10-14").captureToImage().toPixelMap()
        assertEquals(cyan, map[map.width / 2, 13].withoutAlpha())
    }

    @Test
    fun monthCalendarGrid_todayAndSelected_hasOuterCyanRingAroundTheInnerLimeBorder() {
        var cyan = Color.Unspecified
        var lime = Color.Unspecified
        var container = Color.Unspecified
        rule.setContent {
            AppTheme {
                cyan = AppTheme.colors.athleticCyan
                lime = AppTheme.colors.neonLime
                container = AppTheme.colors.selectedContainer
                MonthCalendarGrid(
                    YearMonth(2026, 10),
                    emptySet(),
                    LocalDate(2026, 10, 14),
                    LocalDate(2026, 10, 14),
                    {},
                    {},
                    {},
                    true,
                )
            }
        }
        rule.waitForIdle()

        val map = rule.onNodeWithTag("month_calendar_day_2026-10-14").captureToImage().toPixelMap()
        val x = map.width / 2
        // xxhdpi = 3 px/dp: cyan ring from 4 dp (1.5 dp wide); Modifier.border does not inset, so the 2 dp padding
        // puts the 1 dp lime border at 6 dp, just inside the ring; then the fill.
        assertEquals(cyan, map[x, 14].withoutAlpha())
        assertEquals(lime, map[x, 19].withoutAlpha())
        assertEquals(container, map[x, 30].withoutAlpha())
    }

    @Test
    fun monthCalendarGrid_todaySelectedCell_isDescribedAsTodayAndSelected() {
        showCalendar(selected = LocalDate(2026, 10, 14), today = LocalDate(2026, 10, 14))

        assertEquals("14 de octubre, sin registros, hoy", description("2026-10-14"))
        rule.onNodeWithTag("month_calendar_day_2026-10-14").assertStateDescription("seleccionado")
    }

    @Test
    fun monthCalendarGrid_otherDay_isNotDescribedAsToday() {
        showCalendar(selected = null, today = LocalDate(2026, 10, 14))

        assertEquals("15 de octubre, sin registros", description("2026-10-15"))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertStateDescription(expected: String) {
        assertEquals(expected, fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    private fun Color.withoutAlpha(): Color = copy(alpha = 1f)
}
