package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared duration formatter keeps its output after moving into the design system. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AppDurationFormatTest {
    @get:Rule
    val rule = createComposeRule()

    private fun assertDuration(minutes: Long, expected: String) {
        rule.setContent { AppTheme { Text(durationText(minutes * 60), Modifier.testTag("duration")) } }
        rule.onNodeWithTag("duration").assertTextEquals(expected)
    }

    @Test
    fun fifteenMinutes() = assertDuration(15, "15 min")

    @Test
    fun fortyFiveMinutes() = assertDuration(45, "45 min")

    @Test
    fun anHour_readsZeroMinutes() = assertDuration(60, "1 h 0 min")

    @Test
    fun anHourAndFive() = assertDuration(65, "1 h 5 min")

    @Test
    fun anHourAndAHalf() = assertDuration(90, "1 h 30 min")

    @Test
    fun threeHours() = assertDuration(180, "3 h 0 min")
}
