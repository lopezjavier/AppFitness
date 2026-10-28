package io.github.lopezjavier.appfitness.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeHealthDataSource
import io.github.lopezjavier.appfitness.app.FakeHealthHistoryProbe
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ADR-40: the finished diagnostic survives recreation; a running one comes back idle, never a stuck spinner. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class HealthDiagnosticsRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val source = FakeHealthDataSource()
    private val probe = FakeHealthHistoryProbe()
    private var permissionRequests = 0
    private var deliverResult: () -> Unit = {}

    private val launcher = object : HealthConnectLauncher {
        override fun requestPermissions() {
            permissionRequests++
        }

        override fun openInstall() = Unit
    }

    @Composable
    private fun Host(state: SavedStateHandle) {
        val model = remember { HealthDiagnosticsViewModel(source, probe, state) }
        AppTheme {
            HealthDiagnosticsRoot(
                onBack = {},
                viewModel = model,
                launcherFor = { onResult ->
                    deliverResult = onResult
                    launcher
                },
            )
        }
    }

    private fun check() {
        rule.onNodeWithTag("health_diagnostics_check_button").performClick()
        rule.waitForIdle()
        rule.runOnUiThread { deliverResult() }
        rule.waitForIdle()
    }

    @Test
    fun tappingComprobar_requestsPermissionsThenShowsTheValues() {
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()

        check()

        assertEquals(1, permissionRequests)
        rule.onNodeWithTag("health_diagnostics_recent_row").assertTextContains("Pasos: 10 · Peso: 0")
    }

    @Test
    fun result_survivesRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(savedState) }
        rule.waitForIdle()
        check()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("health_diagnostics_old_row").assertTextContains("Pasos: 5 · Peso: 0")
        rule.onNodeWithTag("health_diagnostics_check_button").assertIsEnabled()
    }

    @Test
    fun runningState_restoresAsIdle() {
        val savedState = SavedStateHandle()
        probe.gate = CompletableDeferred()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(savedState) }
        rule.waitForIdle()
        check()
        rule.onNodeWithTag("health_diagnostics_check_button").assertIsNotEnabled()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("health_diagnostics_check_button").assertIsEnabled()
        // The recreated view model reads only what the handle kept: no result was stored, so it is idle.
        val recreated = HealthDiagnosticsViewModel(source, probe, savedState)

        assertEquals(HealthDiagnosticsState.Idle, recreated.state.value)
    }

    @Test
    fun recreatedViewModel_keepsTheResultValues() {
        val savedState = SavedStateHandle()
        rule.setContent { Host(savedState) }
        rule.waitForIdle()
        check()

        val recreated = HealthDiagnosticsViewModel(source, probe, savedState)

        assertEquals(HealthDiagnosticsState.Result(probe.result), recreated.state.value)
    }
}
