package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.app.FakeHealthDataSource
import io.github.lopezjavier.appfitness.app.FakeHealthSyncRepository
import io.github.lopezjavier.appfitness.app.FakeNutritionGoalsRepository
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.HealthAccess
import io.github.lopezjavier.appfitness.domain.model.HealthMetric
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.WeightChartPreferences
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.SyncHealthMeasurements
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateCircumference
import io.github.lopezjavier.appfitness.domain.usecase.WaistHipRatio
import io.github.lopezjavier.appfitness.domain.usecase.WeightChartRange
import io.github.lopezjavier.appfitness.domain.usecase.WeightTrend
import io.github.lopezjavier.appfitness.platform.HealthConnectLauncher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object HealthClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 22)

    override fun now(): Instant = Instant.parse("2026-10-22T10:30:00Z")
}

/** The banner inside [WeightRoot]: the permission flow, and the denied state surviving recreation (ADR-40). */
private class InMemoryChartPreferences : WeightChartPreferences {
    private val state = MutableStateFlow(WeightChartRange.Default)
    override val range: Flow<WeightChartRange> = state

    override suspend fun setRange(range: WeightChartRange) {
        state.value = range
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h2400dp-xxhdpi")
class HealthSyncRestorationTest {
    @get:Rule
    val rule = createComposeRule()

    private val source = FakeHealthDataSource().apply { access = HealthAccess.NONE }
    private val syncRepository = FakeHealthSyncRepository()
    private val weights = FakeWeightRepository()
    private var permissionRequests = 0
    private var installOffers = 0
    private var deliverResult: (Boolean) -> Unit = {}

    private val launcher = object : HealthConnectLauncher {
        override fun requestPermissions() {
            permissionRequests++
        }

        override fun openInstall() {
            installOffers++
        }
    }

    private fun healthViewModel(state: SavedStateHandle) = HealthSyncViewModel(
        source = source,
        syncHealthMeasurements = SyncHealthMeasurements(source, syncRepository, HealthClock),
        repository = syncRepository,
        connectivity = FakeConnectivityObserver(),
        clock = HealthClock,
        savedState = state,
        tick = { awaitCancellation() },
    )

    private fun weightViewModel() = WeightViewModel(
        repository = weights,
        measurementsByDay = MeasurementsByDay(),
        saveBodyMeasurement = SaveBodyMeasurement(weights, HealthClock, TimeZone.UTC),
        validate = ValidateBodyMeasurement(),
        connectivity = FakeConnectivityObserver(),
        clock = HealthClock,
        timeZone = TimeZone.UTC,
    )

    @Composable
    private fun Host(state: SavedStateHandle) {
        val health = remember { healthViewModel(state) }
        val weight = remember { weightViewModel() }
        val circumferences = remember {
            CircumferencesViewModel(
                repository = FakeCircumferenceRepository(),
                validate = ValidateCircumference(),
                waistHipRatio = WaistHipRatio(),
                connectivity = FakeConnectivityObserver(),
                clock = HealthClock,
                timeZone = TimeZone.UTC,
            )
        }
        val trend = remember {
            WeightTrendViewModel(
                measurements = weights,
                goals = FakeNutritionGoalsRepository(),
                preferences = InMemoryChartPreferences(),
                weightTrend = WeightTrend(),
                clock = HealthClock,
                timeZone = TimeZone.UTC,
            )
        }
        AppTheme {
            WeightRoot(
                modifier = Modifier,
                viewModel = weight,
                trendViewModel = trend,
                circumferencesViewModel = circumferences,
                healthSyncViewModel = health,
                launcherFor = { onResult ->
                    deliverResult = onResult
                    launcher
                },
            )
        }
    }

    @Test
    fun tappingWithoutAccess_requestsPermissionAndADenialShowsPermisoNecesario() {
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_hc_sync_button").performClick()
        rule.waitForIdle()
        assertEquals(1, permissionRequests)

        rule.runOnUiThread { deliverResult(false) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_hc_status").assertTextEquals("Permiso necesario")
        rule.onNodeWithTag("weight_hc_permission_button").assertIsDisplayed()
    }

    @Test
    fun grantedPermission_syncsAndShowsSincronizadoAhora() {
        rule.setContent { Host(SavedStateHandle()) }
        rule.waitForIdle()
        rule.onNodeWithTag("weight_hc_sync_button").performClick()
        rule.waitForIdle()
        source.access = HealthAccess(HealthMetric.entries.toSet(), canWrite = true)

        rule.runOnUiThread { deliverResult(true) }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_hc_status").assertTextEquals("Sincronizado ahora")
    }

    @Test
    fun deniedState_survivesRecreation() {
        val savedState = SavedStateHandle()
        val tester = StateRestorationTester(rule)
        tester.setContent { Host(savedState) }
        rule.waitForIdle()
        rule.onNodeWithTag("weight_hc_sync_button").performClick()
        rule.waitForIdle()
        rule.runOnUiThread { deliverResult(false) }
        rule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("weight_hc_status").assertTextEquals("Permiso necesario")
    }

    @Test
    fun recreatedViewModel_keepsTheDeniedFlagAndRecomputesTheRest() {
        val savedState = SavedStateHandle()
        rule.setContent { Host(savedState) }
        rule.waitForIdle()
        rule.runOnUiThread { deliverResult(false) }
        rule.waitForIdle()

        val recreated = healthViewModel(savedState)
        rule.waitForIdle()

        assertEquals(HealthSyncStatus.PermissionNeeded, recreated.uiState.value.status)
    }
}
