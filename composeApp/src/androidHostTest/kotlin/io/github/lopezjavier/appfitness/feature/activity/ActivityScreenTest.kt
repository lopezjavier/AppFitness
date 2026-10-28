package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val Today = LocalDate(2026, 10, 22)
private val Yesterday = LocalDate(2026, 10, 21)

private fun content(
    sleep: DayValue? = DayValue(435, Today),
    steps: DayValue? = DayValue(8500, Yesterday),
    activeKcal: DayValue? = DayValue(620, Yesterday),
    totalKcal: DayValue? = DayValue(2300, Yesterday),
    restingHeartRate: DayValue? = DayValue(52, Today),
) = ActivityUiState.Content(sleep, steps, activeKcal, totalKcal, restingHeartRate, Today)

/** US-61 "Actividad": the stored watch values as read-only cards, plus loading, empty, error and offline. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per scenario.
class ActivityScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<ActivityEvent>()
    private var weightOpens = 0
    private var backs = 0

    private fun show(state: ActivityUiState, isOnline: MutableStateFlow<Boolean> = MutableStateFlow(false)) {
        rule.setContent {
            AppTheme {
                ActivityScreen(
                    state = state,
                    isOnline = isOnline,
                    onEvent = { events += it },
                    onOpenWeight = { weightOpens++ },
                    onBack = { backs++ },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun title_isActividad_andBackLeaves() {
        show(content())

        rule.onNodeWithTag("activity_title").assertTextEquals("Actividad")
        rule.onNodeWithTag("activity_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun lastNightsSleep_showsHoursAndMinutesAndTheDay() {
        show(content())

        rule.onNodeWithTag("activity_sleep_card").assertTextEquals("Sueño", "7 h 15 min", "Hoy")
    }

    @Test
    fun yesterdaysSteps_areFormattedWithThousandsSeparator() {
        show(content())

        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "8.500 pasos", "Ayer")
    }

    @Test
    fun caloriesAndRestingHeartRate_haveTheirOwnCards() {
        show(content())

        rule.onNodeWithTag("activity_active_kcal_card").assertTextEquals("Calorías activas", "620 kcal", "Ayer")
        rule.onNodeWithTag("activity_total_kcal_card").assertTextEquals("Calorías totales", "2.300 kcal", "Ayer")
        rule.onNodeWithTag("activity_resting_hr_card")
            .assertTextEquals("Frecuencia cardiaca en reposo", "52 lpm", "Hoy")
    }

    @Test
    fun aCardReadsLabelValueAndDay_forAScreenReader() {
        show(content())

        rule.onNodeWithTag("activity_steps_card").assertContentDescriptionEquals("Pasos, 8.500 pasos, Ayer")
    }

    @Test
    fun aValueOlderThanYesterday_isDatedWithItsWeekday() {
        show(content(steps = DayValue(9100, LocalDate(2026, 10, 19))))

        rule.onNodeWithTag("activity_steps_card").assertTextEquals("Pasos", "9.100 pasos", "lun, 19 oct")
    }

    @Test
    fun aTypeTheWatchDoesNotProvide_hasNoCardAndNoError() {
        show(content(sleep = null, totalKcal = null))

        rule.onNodeWithTag("activity_sleep_card").assertDoesNotExist()
        rule.onNodeWithTag("activity_total_kcal_card").assertDoesNotExist()
        rule.onNodeWithTag("activity_steps_card").assertExists()
        rule.onNodeWithTag("activity_error").assertDoesNotExist()
        rule.onNodeWithTag("activity_empty").assertDoesNotExist()
    }

    @Test
    fun cards_areReadOnly() {
        show(content())

        rule.onNodeWithTag("activity_steps_card").assertHasNoClickAction()
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(ActivityUiState.Loading)

        rule.onNodeWithTag("activity_loading").assertExists()
        rule.onNodeWithTag("activity_steps_card").assertDoesNotExist()
    }

    @Test
    fun empty_pointsToSincronizarOnPeso_andTheActionOpensPeso() {
        show(ActivityUiState.Empty)

        rule.onNodeWithTag("activity_empty_title").assertTextEquals("Sin datos de actividad")
        rule.onNodeWithTag("activity_empty_message")
            .assertTextEquals(
                "Se sincroniza al abrir la app o pulsando «Sincronizar» en Peso para traer tu sueño, " +
                    "pasos y calorías desde Health Connect.",
            )
        rule.onNodeWithTag("activity_empty_action").performClick()

        assertEquals(1, weightOpens)
    }

    @Test
    fun error_offersRetry() {
        show(ActivityUiState.Error)

        rule.onNodeWithTag("activity_error_message")
            .assertTextEquals("No se pudo cargar la actividad. Inténtalo de nuevo.")
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
}
