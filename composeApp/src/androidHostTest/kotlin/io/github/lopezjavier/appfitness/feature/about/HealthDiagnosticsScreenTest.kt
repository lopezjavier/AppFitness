package io.github.lopezjavier.appfitness.feature.about

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.HistoryProbeResult
import io.github.lopezjavier.appfitness.domain.model.HistoryWindows
import io.github.lopezjavier.appfitness.domain.model.WindowStats
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** T-139: the temporary diagnostic renders the raw probe values as Spanish text and exposes the "Comprobar" button. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class HealthDiagnosticsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private var checks = 0
    private var backs = 0
    private val state = mutableStateOf<HealthDiagnosticsState>(HealthDiagnosticsState.Idle)

    private val values = HistoryProbeResult(
        featureAvailable = true,
        historyGranted = false,
        steps = HistoryWindows(
            recent = WindowStats(10, Instant.parse("2026-09-01T12:00:00Z")),
            old = WindowStats(5, Instant.parse("2026-07-20T12:00:00Z")),
        ),
        weight = HistoryWindows(recent = WindowStats(3, null), old = WindowStats.EMPTY),
    )

    private fun show() {
        rule.setContent {
            AppTheme {
                HealthDiagnosticsScreen(
                    state = state.value,
                    onCheck = { checks++ },
                    onBack = { backs++ },
                    timeZone = TimeZone.UTC,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun initially_showsTheFiveRowsWithoutValuesAndTheCheckButton() {
        show()

        listOf("feature", "history", "old", "recent", "oldest").forEach {
            rule.onNodeWithTag("health_diagnostics_${it}_row").assertIsDisplayed().assertTextContains("-")
        }
        rule.onNodeWithTag("health_diagnostics_check_button").assertIsDisplayed().assertTextContains("Comprobar")
    }

    @Test
    fun tappingComprobar_asksForTheCheck() {
        show()

        rule.onNodeWithTag("health_diagnostics_check_button").performClick()

        assertEquals(1, checks)
    }

    @Test
    fun result_showsTheFiveValuesFormatted() {
        state.value = HealthDiagnosticsState.Result(values)
        show()

        rule.onNodeWithTag("health_diagnostics_feature_row").assertTextContains("Sí")
        rule.onNodeWithTag("health_diagnostics_history_row").assertTextContains("No")
        rule.onNodeWithTag("health_diagnostics_old_row").assertTextContains("Pasos: 5 · Peso: 0")
        rule.onNodeWithTag("health_diagnostics_recent_row").assertTextContains("Pasos: 10 · Peso: 3")
        rule.onNodeWithTag("health_diagnostics_oldest_row").assertTextContains("Pasos: 20/07/2026 · Peso: -")
    }

    @Test
    fun result_withTheFeatureUnavailable_showsNoDisponible() {
        state.value = HealthDiagnosticsState.Result(values.copy(featureAvailable = false))
        show()

        rule.onNodeWithTag("health_diagnostics_feature_row").assertTextContains("No disponible")
    }

    @Test
    fun running_disablesTheButton() {
        state.value = HealthDiagnosticsState.Running
        show()

        rule.onNodeWithTag("health_diagnostics_check_button").assertIsNotEnabled()
    }

    @Test
    fun unavailable_showsTheInstallMessage() {
        state.value = HealthDiagnosticsState.Unavailable(HealthAvailability.NotInstalled)
        show()

        rule.onNodeWithTag("health_diagnostics_unavailable").assertTextEquals("Instala Health Connect")
    }

    @Test
    fun unavailable_updateRequired_showsTheUpdateMessage() {
        state.value = HealthDiagnosticsState.Unavailable(HealthAvailability.UpdateRequired)
        show()

        rule.onNodeWithTag("health_diagnostics_unavailable").assertTextEquals("Actualiza Health Connect")
    }

    @Test
    fun error_showsTheMessage() {
        state.value = HealthDiagnosticsState.Error
        show()

        rule.onNodeWithTag("health_diagnostics_error").assertIsDisplayed()
    }

    @Test
    fun back_isForwarded() {
        show()

        rule.onNodeWithTag("health_diagnostics_back").performClick()

        assertEquals(1, backs)
    }
}
