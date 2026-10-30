package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** US-76: the read-only "Uso de IA" screen and the shared failure messages. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class CoachUsageScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<CoachUsageEvent>()
    private var backs = 0

    private fun show(state: CoachUsageUiState) {
        rule.setContent {
            AppTheme { CoachUsageScreen(state, flowOf(true), { events += it }, { backs++ }) }
        }
        rule.waitForIdle()
    }

    private fun usage(cost: Double) = CoachUsageUiState.Content(CoachUsage(LocalDate(2026, 11, 1), 0, 0, cost))

    @Test
    fun usage_isShownAsAmountOfLimit_withTheExplanation() {
        show(usage(0.42))

        rule.onNodeWithTag("coach_usage_figure").assertTextEquals("0,42 € de 2,00 €")
        rule.onNodeWithTag("coach_usage_progress").assertExists()
        rule.onNodeWithTag("coach_usage_explanation")
            .assertTextEquals("Gasto de este mes. El límite se renueva el día 1.")
        rule.onNodeWithTag("coach_usage_limit_message").assertDoesNotExist()
        rule.onNodeWithTag("coach_usage_ping").assertDoesNotExist()
    }

    @Test
    fun zeroUsage_showsZeroOfTheLimit() {
        show(usage(0.0))

        rule.onNodeWithTag("coach_usage_figure").assertTextEquals("0,00 € de 2,00 €")
    }

    @Test
    fun atTheLimit_showsTheLimitMessage() {
        show(usage(2.0))

        rule.onNodeWithTag("coach_usage_figure").assertTextEquals("2,00 € de 2,00 €")
        rule.onNodeWithTag("coach_usage_limit_message")
            .assertTextEquals("Has alcanzado el límite mensual de IA. Vuelve a probar el mes que viene.")
    }

    @Test
    fun loading_showsTheSkeleton() {
        show(CoachUsageUiState.Loading)

        rule.onNodeWithTag("coach_usage_loading").assertExists()
        rule.onNodeWithTag("coach_usage_figure").assertDoesNotExist()
    }

    @Test
    fun error_offersReintentar_thatSendsRetry() {
        show(CoachUsageUiState.Error)

        rule.onNodeWithText("No se pudo cargar el uso de IA. Inténtalo de nuevo.").assertExists()
        rule.onNodeWithTag("coach_usage_retry").performClick()

        assertEquals(listOf<CoachUsageEvent>(CoachUsageEvent.Retry), events)
    }

    @Test
    fun offline_showsTheOfflineState() {
        show(CoachUsageUiState.Offline)

        rule.onNodeWithTag("coach_usage_offline").assertExists()
        rule.onNodeWithText("Sin conexión").assertExists()
    }

    @Test
    fun backButton_goesBack() {
        show(usage(0.1))

        rule.onNodeWithTag("coach_usage_back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun limitMessage_wordsEachFailureForTheUser() {
        val failures = mapOf(
            CoachFailure.LimitReached(CoachFailure.Scope.User) to
                "Has alcanzado el límite mensual de IA. Vuelve a probar el mes que viene.",
            CoachFailure.LimitReached(CoachFailure.Scope.Global) to
                "La IA no está disponible ahora mismo. Inténtalo más tarde.",
            CoachFailure.NoCredit to "La IA no está disponible ahora mismo. Inténtalo más tarde.",
            CoachFailure.Timeout to "No se pudo completar la respuesta de IA. Inténtalo de nuevo.",
            CoachFailure.Other to "No se pudo usar la IA. Inténtalo de nuevo.",
        )
        rule.setContent {
            AppTheme {
                Column {
                    failures.keys.forEachIndexed { index, failure ->
                        CoachLimitMessage(failure, testTag = "msg_$index")
                    }
                }
            }
        }
        rule.waitForIdle()

        failures.values.forEachIndexed { index, text -> rule.onNodeWithTag("msg_$index").assertTextEquals(text) }
    }
}
