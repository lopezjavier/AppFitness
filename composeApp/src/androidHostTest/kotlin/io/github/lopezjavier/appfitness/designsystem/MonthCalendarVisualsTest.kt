package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import org.junit.Assert.assertTrue
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
    fun monthCalendarGrid_selectedDay_isALitLimeDisc() {
        var base = Color.Unspecified
        rule.setContent {
            AppTheme {
                base = AppTheme.colors.surfaceBase
                Box(Modifier.background(base)) {
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
        }
        rule.waitForIdle()

        val selected = rule.onNodeWithTag("month_calendar_day_2026-10-14")
        val other = rule.onNodeWithTag("month_calendar_day_2026-10-15")
        val selectedMap = selected.captureToImage().toPixelMap()
        val otherMap = other.captureToImage().toPixelMap()
        val x = selectedMap.width / 2
        // 48 dp cell, 4 dp padding (xxhdpi = 3 px/dp): the lit disc starts at 12 px and is a lime gradient (R-64).
        assertLimeFill(selectedMap[x, 24].withoutAlpha())
        assertLimeFill(selectedMap[x, 30].withoutAlpha())
        // C-33: a resting day draws no circle, so its pixels are the plain background.
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
        rule.setContent {
            AppTheme {
                cyan = AppTheme.colors.athleticCyan
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
        // xxhdpi = 3 px/dp: the cyan ring starts at 12 px (4 dp); the lit lime disc is inset by the ring and a gap.
        assertEquals(cyan, map[x, 14].withoutAlpha())
        assertLimeFill(map[x, 30].withoutAlpha())
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

    /** The lit disc is a gradient of the lime ramp, so it is checked by hue (bright green-yellow), not by one value. */
    private fun assertLimeFill(color: Color) {
        val lime = color.green > 0.8f && color.green > color.red + 0.1f && color.blue < 0.5f
        assertTrue("not a lime fill: $color", lime)
    }
}
