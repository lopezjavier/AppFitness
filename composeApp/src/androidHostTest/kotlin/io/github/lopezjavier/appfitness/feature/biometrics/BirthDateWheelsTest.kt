package io.github.lopezjavier.appfitness.feature.biometrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BirthDateWheelsTest {
    @get:Rule
    val rule = createComposeRule()

    private var date by mutableStateOf(LocalDate(1990, 1, 31))
    private val centredDates = mutableListOf<LocalDate>()

    private fun show() {
        rule.setContent {
            AppTheme {
                BirthDateWheels(
                    date = date,
                    today = LocalDate(2026, 10, 14),
                    onChange = { date = it },
                    tagPrefix = "t",
                    onCentredChange = { centredDates += it },
                )
            }
        }
    }

    @Test
    fun scrollingTheMonthWheelThroughFebruary_keepsDay31WhenItLandsOnAMonthWithThirtyOneDays() {
        show()
        rule.onNodeWithTag("t_birth_month_picker").performTouchInput { swipeUp() }
        rule.waitForIdle()

        val landed = date.month.number
        assertTrue("the swipe must pass February, landed on month $landed", landed > 2)
        val monthDays = listOf(3, 5, 7, 8, 10, 12)
        if (landed in monthDays) {
            assertEquals("day must stay 31 after passing February (landed $date)", 31, date.day)
        }
        assertEquals(1990, date.year)
    }

    @Test
    fun centredFeed_followsTheWheelWithoutChangingTheCommittedDateBeforeItSettles() {
        show()
        rule.onNodeWithTag("t_birth_month_picker").performTouchInput { swipeUp() }
        rule.waitForIdle()

        assertTrue("the live feed reports the centred date, got $centredDates", centredDates.isNotEmpty())
        assertEquals(date, centredDates.last())
    }
}
