package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 22)

/** ADR-40: the scroll position of "Actividad" survives recreation (the ViewModel holds no state of its own). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h300dp-xxhdpi")
class ActivityRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val content = ActivityUiState.Content(
        sleep = DayValue(435, Today),
        steps = DayValue(8500, Today),
        activeKcal = DayValue(620, Today),
        totalKcal = DayValue(2300, Today),
        restingHeartRate = DayValue(52, Today),
        today = Today,
    )

    @Test
    fun scrollPosition_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AppTheme {
                ActivityScreen(content, MutableStateFlow(true), {}, {}, {})
            }
        }
        rule.onNodeWithTag("activity_resting_hr_card").performScrollTo()
        rule.onNodeWithTag("activity_resting_hr_card").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_resting_hr_card").assertIsDisplayed()
    }
}
