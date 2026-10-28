package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.usecase.ActivityMetric
import io.github.lopezjavier.appfitness.domain.usecase.SleepStageSummary
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncEvent
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncStatus
import io.github.lopezjavier.appfitness.feature.weight.HealthSyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-64 "Actividad": the rendering of every state, driven by a fixed [ActivityUiState] and its recorded events. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per behaviour.
class ActivityScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ActivityEvent>()
    private val healthEvents = mutableListOf<HealthSyncEvent>()
    private var backs = 0

    private fun show(
        state: ActivityUiState,
        isOnline: MutableStateFlow<Boolean> = MutableStateFlow(false),
        health: HealthSyncUiState = HealthSyncUiState(HealthSyncStatus.NeverSynced),
    ) {
        rule.setContent {
            AppTheme {
                ActivityScreen(
                    state = state,
                    isOnline = isOnline,
                    onEvent = { events += it },
                    healthSync = health,
                    onHealthEvent = { healthEvents += it },
                    onBack = { backs++ },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun title_isActividad_andBackLeaves() {
        show(activityContent())

        rule.onNodeWithTag("activity_title").assertTextEquals("Actividad")
        rule.onNodeWithTag("activity_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun summaryOfToday_hasOneCardPerMetricWithItsValue() {
        show(activityContent())

        rule.onNodeWithTag("activity_summary_card").assertTextEquals("Hoy")
        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "8.500")
        rule.onNodeWithTag("activity_sleep_card").assertTextEquals("Sueño", "7 h 15 min")
        rule.onNodeWithTag("activity_active_kcal_card").assertTextEquals("Calorías activas", "620 kcal")
        rule.onNodeWithTag("activity_total_kcal_card").assertTextEquals("Calorías totales", "2.300 kcal")
        rule.onNodeWithTag("activity_resting_hr_card").assertTextEquals("Frecuencia cardiaca en reposo", "52 lpm")
    }

    @Test
    fun aMetricWithoutData_saysSinDatos_andIsNotAnError() {
        show(activityContent(sleep = null, totalKcal = null))

        rule.onNodeWithTag("activity_sleep_card").assertTextEquals("Sueño", "Sin datos")
        rule.onNodeWithTag("activity_total_kcal_card").assertTextEquals("Calorías totales", "Sin datos")
        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "8.500")
        rule.onNodeWithTag("activity_error").assertDoesNotExist()
        rule.onNodeWithTag("activity_day_empty").assertDoesNotExist()
    }

    @Test
    fun aDayWithNothing_saysSoAndKeepsTheCards() {
        show(
            activityContent(
                day = LocalDate(2026, 10, 10),
                sleep = null,
                steps = null,
                activeKcal = null,
                totalKcal = null,
                restingHeartRate = null,
            ),
        )

        rule.onNodeWithTag("activity_day_empty").assertTextEquals("Sin datos de este día.")
        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "Sin datos")
    }

    @Test
    fun anotherDay_showsItsDate_andTheChipGoesBackToToday() {
        show(activityContent(day = LocalDate(2026, 10, 10)))

        rule.onNodeWithTag("activity_summary_card").assertTextEquals("10 de octubre")
        rule.onNodeWithTag("activity_back_to_today").performClick()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.GoToToday), events)
    }

    @Test
    fun today_hasNoBackToTodayChip() {
        show(activityContent())

        rule.onNodeWithTag("activity_back_to_today").assertDoesNotExist()
    }

    @Test
    fun tappingACard_selectsThatMetricInTheChart() {
        show(activityContent())

        rule.onNodeWithTag("activity_sleep_card").performClick()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.SelectMetric(ActivityMetric.Sleep)), events)
    }

    @Test
    fun theCardOfTheChartMetric_isSelected() {
        show(activityContent(metric = ActivityMetric.TotalKcal))

        rule.onNodeWithTag("activity_total_kcal_card").assertIsSelected()
    }

    @Test
    fun theChart_defaultsToSteps() {
        show(activityContent())

        rule.onNodeWithTag("activity_chart_metric_trigger").assertTextEquals("Pasos")
        rule.onNodeWithTag("activity_chart").performScrollTo().assertExists()
        rule.onNodeWithTag("activity_chart_empty").assertDoesNotExist()
    }

    @Test
    fun theMetricTrigger_opensThePickerAndAppliesTheChoice() {
        show(activityContent())

        rule.onNodeWithTag("activity_chart_metric_trigger").performScrollTo().performClick()
        rule.onNodeWithTag("activity_chart_metric_picker_sheet_title").assertTextEquals("Métrica del gráfico")
        rule.onNodeWithTag("activity_chart_metric_steps").assertIsSelected()
        rule.onNodeWithTag("activity_chart_metric_resting_hr").performClick()
        rule.waitForIdle()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.SelectMetric(ActivityMetric.RestingHeartRate)), events)
        rule.onNodeWithTag("activity_chart_metric_picker_sheet_title").assertDoesNotExist()
    }

    @Test
    fun aMetricWithoutPoints_saysThereIsNoData_insteadOfFailing() {
        show(activityContent(metric = ActivityMetric.RestingHeartRate, points = emptyList(), restingHeartRate = null))

        rule.onNodeWithTag("activity_chart_empty")
            .assertTextEquals("Sin datos de frecuencia cardiaca en reposo en este periodo.")
        rule.onNodeWithTag("activity_chart").assertDoesNotExist()
        rule.onNodeWithTag("activity_error").assertDoesNotExist()
    }

    @Test
    fun theRangeSelector_offersFiveRanges_andSendsTheChoice() {
        show(activityContent())

        listOf("week", "month", "threemonths", "sixmonths", "year").forEach {
            rule.onNodeWithTag("activity_chart_range_$it").performScrollTo().assertExists()
        }
        rule.onNodeWithTag("activity_chart_range_month").assertIsSelected()
        rule.onNodeWithTag("activity_chart_range_year").performClick()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.SelectRange(WeightChartRange.Year)), events)
    }

    @Test
    fun sleepWithStages_showsTheDistributionWithTextLegend() {
        show(
            activityContent(
                metric = ActivityMetric.Sleep,
                sleepStages = SleepStageSummary(mapOf("deep" to 80, "light" to 240, "rem" to 90, "awake" to 25)),
            ),
        )

        rule.onNodeWithTag("activity_sleep_stages").performScrollTo().assertExists()
        rule.onNodeWithTag("activity_sleep_stages_deep", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("activity_sleep_total").assertDoesNotExist()
        rule.onNodeWithTag("activity_sleep_stages")
            .assertTextEquals(
                "Profundo",
                "1 h 20 min",
                "Ligero",
                "4 h 0 min",
                "REM",
                "1 h 30 min",
                "Despierto",
                "25 min",
            )
    }

    @Test
    fun sleepWithoutStages_showsTheTotalDurationOnly() {
        show(activityContent(metric = ActivityMetric.Sleep))

        rule.onNodeWithTag("activity_sleep_total").performScrollTo().assertTextEquals("Sueño 7 h 15 min")
        rule.onNodeWithTag("activity_sleep_stages").assertDoesNotExist()
    }

    @Test
    fun otherMetrics_showNoSleepDetail() {
        show(activityContent(sleepStages = SleepStageSummary(mapOf("deep" to 80))))

        rule.onNodeWithTag("activity_sleep_stages").assertDoesNotExist()
        rule.onNodeWithTag("activity_sleep_total").assertDoesNotExist()
    }

    @Test
    fun theCalendarAction_opensTheDayPicker_andAPickedDayIsSent() {
        show(activityContent())

        rule.onNodeWithTag("activity_calendar_action").performClick()
        rule.onNodeWithTag("activity_day_picker_sheet_title").assertTextEquals("Calendario")
        rule.onNodeWithTag("activity_day_picker_today").assertDoesNotExist()
        rule.onNodeWithTag("month_calendar_day_2026-10-15").performClick()
        rule.waitForIdle()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.SelectDay(LocalDate(2026, 10, 15))), events)
        rule.onNodeWithTag("activity_day_picker_sheet_title").assertDoesNotExist()
    }

    @Test
    fun theDayPicker_offersHoyWhenAnotherDayIsShown() {
        show(activityContent(day = LocalDate(2026, 10, 10)))

        rule.onNodeWithTag("activity_calendar_action").performClick()
        rule.onNodeWithTag("activity_day_picker_today").performClick()
        rule.waitForIdle()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.GoToToday), events)
    }

    @Test
    fun aDayThatFailedToLoad_showsAnInlineErrorWithRetry_andKeepsTheChart() {
        show(activityContent(day = LocalDate(2026, 3, 1), dayError = true))

        rule.onNodeWithTag("activity_day_error_message")
            .assertTextEquals("No se pudo cargar ese día. Inténtalo de nuevo.")
        rule.onNodeWithTag("activity_chart_card").performScrollTo().assertExists()
        rule.onNodeWithTag("activity_day_retry").performClick()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.Retry), events)
    }

    @Test
    fun aDayBeingFetched_showsSkeletonsInsteadOfCards() {
        show(activityContent(day = LocalDate(2026, 3, 1), dayLoading = true))

        rule.onNodeWithTag("activity_steps_card").assertDoesNotExist()
        rule.onNodeWithTag("activity_day_error").assertDoesNotExist()
    }

    @Test
    fun loading_showsTheSkeleton_andTheStatusLine() {
        show(ActivityUiState.Loading)

        rule.onNodeWithTag("activity_loading").assertExists()
        rule.onNodeWithTag("activity_status_line").assertExists()
        rule.onNodeWithTag("activity_steps_card").assertDoesNotExist()
        rule.onNodeWithTag("activity_calendar_action").assertDoesNotExist()
    }

    @Test
    fun empty_pointsToSincronizar_withoutAnActionButton() {
        show(ActivityUiState.Empty)

        rule.onNodeWithTag("activity_empty_title").assertTextEquals("Sin datos de actividad")
        rule.onNodeWithTag("activity_empty_message")
            .assertTextEquals("Pulsa Sincronizar para traer los datos de tu reloj.")
        rule.onNodeWithTag("activity_empty_action").assertDoesNotExist()
        rule.onNodeWithTag("weight_hc_sync_button").assertExists()
    }

    @Test
    fun error_offersRetry_andKeepsTheStatusLine() {
        show(ActivityUiState.Error)

        rule.onNodeWithTag("activity_error_message")
            .assertTextEquals("No se pudo cargar la actividad. Inténtalo de nuevo.")
        rule.onNodeWithTag("activity_status_line").assertExists()
        rule.onNodeWithTag("activity_retry").performClick()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.Retry), events)
    }

    @Test
    fun offline_showsTheOfflineStateAndReloadsWhenTheConnectionReturns() {
        val online = MutableStateFlow(false)
        show(ActivityUiState.Offline, online)

        rule.onNodeWithTag("offline_state").assertExists()
        online.value = true
        rule.waitForIdle()

        assertEquals(listOf<ActivityEvent>(ActivityEvent.Retry), events)
    }

    @Test
    fun theSyncButton_isOnTheScreen_andTapsSyncLikeInPeso() {
        show(activityContent())

        rule.onNodeWithTag("activity_status_line").assertExists()
        rule.onNodeWithTag("weight_hc_status").assertTextEquals("Sin sincronizar")
        rule.onNodeWithTag("weight_hc_sync_button").performClick()

        assertEquals(listOf<HealthSyncEvent>(HealthSyncEvent.Tap), healthEvents)
    }

    @Test
    @Config(qualifiers = "w900dp-h2400dp-xxhdpi")
    fun wideWindow_putsTheSummaryAndTheChartSideBySide() {
        show(activityContent())

        rule.onNodeWithTag("activity_two_column").assertExists()
        rule.onNodeWithTag("activity_chart_card").assertExists()
        rule.onNodeWithTag("activity_steps_card").assertExists()
    }
}
