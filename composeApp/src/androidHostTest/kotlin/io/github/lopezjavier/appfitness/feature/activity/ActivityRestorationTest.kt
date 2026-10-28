package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthActivityRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncStatus
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object RestorationClock : Clock {
    override fun today(): LocalDate = ActivityToday

    override fun now(): Instant = Instant.parse("2026-10-22T10:00:00Z")
}

/**
 * ADR-40: what the user chose on "Actividad" survives recreation. The sheets and the scroll live in the composition;
 * metric, range and day live in the view model's saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h300dp-xxhdpi")
class ActivityRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val content = activityContent()

    @Composable
    private fun Screen(state: ActivityUiState = content) {
        AppTheme {
            ActivityScreen(
                state = state,
                isOnline = MutableStateFlow(true),
                onEvent = {},
                healthSync = HealthSyncUiState(HealthSyncStatus.NeverSynced),
                onHealthEvent = {},
                onBack = {},
            )
        }
    }

    @Test
    fun scrollPosition_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Screen() }
        rule.onNodeWithTag("activity_resting_hr_card").performScrollTo()
        rule.onNodeWithTag("activity_resting_hr_card").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_resting_hr_card").assertIsDisplayed()
    }

    @Test
    fun anOpenDayPicker_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Screen() }
        rule.onNodeWithTag("activity_calendar_action").performClick()
        rule.onNodeWithTag("activity_day_picker_sheet_title").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_day_picker_sheet_title").assertExists()
    }

    @Test
    fun anOpenMetricPicker_survivesRecreation() {
        val tester = StateRestorationTester(rule)
        tester.setContent { Screen() }
        rule.onNodeWithTag("activity_chart_metric_trigger").performScrollTo().performClick()
        rule.onNodeWithTag("activity_chart_metric_picker_sheet_title").assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_chart_metric_picker_sheet_title").assertExists()
    }

    @Test
    fun metricRangeAndDay_surviveAViewModelRecreatedFromTheSameSavedState() {
        val repository = FakeHealthActivityRepository().also {
            it.history = HealthActivityHistory(
                daily = listOf(
                    DailyActivity(LocalDate(2026, 10, 10), 3000, null, null),
                    DailyActivity(ActivityToday, 8500, null, null),
                ),
                sleep = emptyList(),
                restingHeartRate = emptyList(),
            )
        }
        val handle = SavedStateHandle()
        var generation by mutableIntStateOf(0)
        lateinit var model: ActivityViewModel
        rule.setContent {
            // A new view model for each generation, over the same saved state: the process-death path.
            model = remember(generation) {
                ActivityViewModel(repository, RestorationClock, TimeZone.UTC, FakeConnectivityObserver(), handle)
            }
            val state by model.uiState.collectAsState()
            Screen(state)
        }
        rule.waitForIdle()
        model.onEvent(ActivityEvent.SelectMetric(ActivityMetric.Sleep))
        model.onEvent(ActivityEvent.SelectRange(WeightChartRange.Week))
        model.onEvent(ActivityEvent.SelectDay(LocalDate(2026, 10, 10)))
        rule.waitForIdle()

        generation++
        rule.waitForIdle()

        rule.onNodeWithTag("activity_chart_metric_trigger").performScrollTo().assertTextEquals("Sueño")
        rule.onNodeWithTag("activity_chart_range_week").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("activity_summary_card").assertTextEquals("10 de octubre")
    }
}
