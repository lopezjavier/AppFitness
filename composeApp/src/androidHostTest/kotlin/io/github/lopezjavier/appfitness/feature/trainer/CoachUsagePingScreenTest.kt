package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.CoachUsage
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T-161: the dev-only ping block of the "Uso de IA" screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CoachUsagePingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<CoachUsageEvent>()
    private val ping = mutableStateOf<CoachPingUiState>(CoachPingUiState.Idle)

    private fun show(showPing: Boolean) {
        rule.setContent {
            AppTheme {
                CoachUsageScreen(
                    state = CoachUsageUiState.Content(CoachUsage(LocalDate(2026, 11, 1), 0, 0, 0.1)),
                    isOnline = flowOf(true),
                    onEvent = { events += it },
                    onBack = {},
                    showPing = showPing,
                    pingState = ping.value,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun pingBlock_isHidden_whenTheDevFlagIsOff() {
        show(showPing = false)

        rule.onNodeWithTag("coach_usage_ping").assertDoesNotExist()
        rule.onNodeWithTag("coach_usage_ping_status").assertDoesNotExist()
    }

    @Test
    fun pingButton_isShown_andSendsPing_whenTheDevFlagIsOn() {
        show(showPing = true)

        rule.onNodeWithTag("coach_usage_ping").performClick()

        assertEquals(listOf<CoachUsageEvent>(CoachUsageEvent.Ping), events)
        rule.onNodeWithTag("coach_usage_ping_status").assertDoesNotExist()
    }

    @Test
    fun pingStatus_showsRunningSuccessOfflineAndTheFailureReason() {
        val expected = mapOf(
            CoachPingUiState.Running to "Llamando al coach...",
            CoachPingUiState.Success to "Ping correcto.",
            CoachPingUiState.Offline to "Sin conexión. Inténtalo de nuevo.",
            CoachPingUiState.Failed(CoachFailure.Timeout) to
                "No se pudo completar la respuesta de IA. Inténtalo de nuevo.",
        )
        show(showPing = true)

        expected.forEach { (next, text) ->
            ping.value = next
            rule.waitForIdle()
            rule.onNodeWithTag("coach_usage_ping_status").assertTextEquals(text)
        }
    }
}
