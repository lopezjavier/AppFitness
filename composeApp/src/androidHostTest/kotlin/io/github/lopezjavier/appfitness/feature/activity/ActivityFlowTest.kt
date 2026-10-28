package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthActivityRepository
import io.github.lopezjavier.appfitness.app.FakeHealthDataSource
import io.github.lopezjavier.appfitness.app.FakeHealthSyncRepository
import io.github.lopezjavier.appfitness.app.healthSyncCoordinator
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.DailyActivity
import io.github.lopezjavier.appfitness.domain.model.HealthActivityHistory
import io.github.lopezjavier.appfitness.domain.model.SleepSession
import io.github.lopezjavier.appfitness.domain.model.SleepStage
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncViewModel
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object FlowClock : Clock {
    override fun today(): LocalDate = ActivityToday

    override fun now(): Instant = Instant.parse("2026-10-22T10:00:00Z")
}

/** The scenarios of US-64 end to end: the real view models over fakes, driven through the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class ActivityFlowTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeHealthActivityRepository()
    private val source = FakeHealthDataSource()
    private val syncRepository = FakeHealthSyncRepository()

    private fun at(hour: Int) = Instant.parse("2026-10-22T0$hour:00:00Z")

    private fun stored() {
        val stages = listOf(
            SleepStage("deep", at(0), at(1)),
            SleepStage("light", at(1), at(5)),
            SleepStage("rem", at(5), at(6)),
        )
        repository.history = HealthActivityHistory(
            daily = listOf(
                DailyActivity(LocalDate(2026, 10, 10), 3000, 200.0, 1900.0),
                DailyActivity(LocalDate(2026, 10, 15), 5000, 300.0, 2000.0),
                DailyActivity(ActivityToday, 8500, 620.0, 2300.0),
            ),
            sleep = listOf(SleepSession("s", at(0), at(6), 360, stages)),
            restingHeartRate = emptyList(),
        )
    }

    @Composable
    private fun Host() {
        val activity = remember {
            ActivityViewModel(repository, FlowClock, TimeZone.UTC, FakeConnectivityObserver(), SavedStateHandle())
        }
        val health = remember {
            HealthSyncViewModel(
                source = source,
                coordinator = healthSyncCoordinator(
                    scope = CoroutineScope(SupervisorJob()),
                    source = source,
                    repository = syncRepository,
                ),
                repository = syncRepository,
                connectivity = FakeConnectivityObserver(),
                clock = FlowClock,
                tick = { awaitCancellation() },
            )
        }
        AppTheme {
            ActivityRoot(onBack = {}, viewModel = activity, healthSyncViewModel = health, launcherFor = {
                HealthConnectLauncher.None
            })
        }
    }

    private fun open() {
        stored()
        rule.setContent { Host() }
        rule.waitForIdle()
    }

    @Test
    fun opening_showsTodaysFiveValuesWithTheirIcons_andStepsOnTheChart() {
        open()

        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "8.500")
        rule.onNodeWithTag("activity_sleep_card").assertTextEquals("Sueño", "6 h 0 min")
        rule.onNodeWithTag("activity_active_kcal_card").assertTextEquals("Calorías activas", "620 kcal")
        rule.onNodeWithTag("activity_total_kcal_card").assertTextEquals("Calorías totales", "2.300 kcal")
        rule.onNodeWithTag("activity_resting_hr_card").assertTextEquals("Frecuencia cardiaca en reposo", "Sin datos")
        rule.onNodeWithTag("activity_chart_metric_trigger").assertTextEquals("Pasos")
        rule.onNodeWithTag("activity_steps_card").assertIsSelected()
    }

    @Test
    fun syncingFromTheScreen_readsHealthConnect_andReloadsTheStoredData() {
        open()
        val before = repository.historyCalls

        rule.onNodeWithTag("weight_hc_sync_button").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { source.watchReads > 0 }
        rule.waitForIdle()
        assertTrue(repository.historyCalls > before)

        assertTrue(source.watchReads > 0)
        rule.onNodeWithTag("weight_hc_status").assertTextEquals("Sincronizado ahora")
    }

    @Test
    fun selectingAnotherMetric_changesTheChart() {
        open()

        rule.onNodeWithTag("activity_chart_metric_trigger").performScrollTo().performClick()
        rule.onNodeWithTag("activity_chart_metric_sleep").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_chart_metric_trigger").assertTextEquals("Sueño")
        rule.onNodeWithTag("activity_sleep_card").assertIsSelected()
    }

    @Test
    fun selectingARange_marksItSelected() {
        open()

        listOf("week", "threemonths", "sixmonths", "year", "month").forEach {
            rule.onNodeWithTag("activity_chart_range_$it").performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("activity_chart_range_$it").assertIsSelected()
        }
    }

    @Test
    fun sleepMetric_withStages_showsTheDistribution() {
        open()

        rule.onNodeWithTag("activity_sleep_card").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_sleep_stages").performScrollTo()
            .assertTextEquals("Profundo", "1 h 0 min", "Ligero", "4 h 0 min", "REM", "1 h 0 min")
    }

    @Test
    fun sleepMetric_withoutStages_showsTheTotalOnly() {
        stored()
        repository.history = repository.history.copy(
            sleep = listOf(SleepSession("s", at(0), at(6), 360, emptyList())),
        )
        rule.setContent { Host() }
        rule.waitForIdle()

        rule.onNodeWithTag("activity_sleep_card").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_sleep_total").performScrollTo().assertTextEquals("Sueño 6 h 0 min")
        rule.onNodeWithTag("activity_sleep_stages").assertDoesNotExist()
    }

    @Test
    fun pickingADayWithTheCalendar_showsItsValues_andHoyComesBack() {
        open()

        rule.onNodeWithTag("activity_calendar_action").performClick()
        rule.onNodeWithTag("month_calendar_day_2026-10-10").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_summary_card").assertTextEquals("10 de octubre")
        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "3.000")
        rule.onNodeWithTag("activity_sleep_card").assertTextEquals("Sueño", "Sin datos")

        rule.onNodeWithTag("activity_back_to_today").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "8.500")
    }

    @Test
    fun tappingOutsideTheCalendar_closesIt_andKeepsTheSelectedDay() {
        open()

        rule.onNodeWithTag("activity_calendar_action").performClick()
        rule.onNodeWithTag("activity_day_picker_sheet").assertIsDisplayed()
        // The scrim ("Close sheet") exists only when dismissOnClickOutside is on; the X is labelled "Cerrar".
        rule.onNodeWithContentDescription("Close sheet", ignoreCase = true).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_day_picker_sheet").assertDoesNotExist()
        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "8.500")
    }

    @Test
    fun aDayWithoutData_saysSo() {
        open()

        rule.onNodeWithTag("activity_calendar_action").performClick()
        rule.onNodeWithTag("month_calendar_day_2026-10-12").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_day_empty").assertTextEquals("Sin datos de este día.")
    }

    @Test
    fun aMetricTheWatchDoesNotWrite_saysThereIsNoData_andDoesNotFail() {
        open()

        rule.onNodeWithTag("activity_resting_hr_card").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("activity_chart_empty")
            .performScrollTo()
            .assertTextEquals("Sin datos de frecuencia cardiaca en reposo en este periodo.")
        rule.onNodeWithTag("activity_error").assertDoesNotExist()
    }
}
