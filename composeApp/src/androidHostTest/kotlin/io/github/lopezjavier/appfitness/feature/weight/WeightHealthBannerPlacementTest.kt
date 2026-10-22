package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.SavedStateHandle
import io.github.lopezjavier.appfitness.app.FakeConnectivityObserver
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.usecase.MeasurementsByDay
import io.github.lopezjavier.appfitness.domain.usecase.SaveBodyMeasurement
import io.github.lopezjavier.appfitness.domain.usecase.ValidateBodyMeasurement
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private object PlacementClock : Clock {
    override fun today(): LocalDate = LocalDate(2026, 10, 22)

    override fun now(): Instant = Instant.parse("2026-10-22T10:30:00Z")
}

/** The Health Connect card sits below the primary "Registrar peso de hoy" action (UX notes 1). */
@RunWith(RobolectricTestRunner::class)
class WeightHealthBannerPlacementTest {
    @get:Rule
    val rule = createComposeRule()

    private val repository = FakeWeightRepository(
        BodyMeasurement("a", Instant.parse("2026-10-20T07:30:00Z"), 75.2, MeasurementSource.Manual),
    )

    @Composable
    private fun Host(health: HealthSyncUiState) {
        val model = WeightViewModel(
            repository = repository,
            measurementsByDay = MeasurementsByDay(),
            saveBodyMeasurement = SaveBodyMeasurement(repository, PlacementClock, TimeZone.UTC),
            validate = ValidateBodyMeasurement(),
            connectivity = FakeConnectivityObserver(),
            clock = PlacementClock,
            timeZone = TimeZone.UTC,
            savedState = SavedStateHandle(),
        )
        val state by model.uiState.collectAsState()
        AppTheme {
            WeightScreen(
                state,
                model::onEvent,
                CircumferencesUiState.Loading,
                {},
                healthSync = health,
                timeZone = TimeZone.UTC,
            )
        }
    }

    private fun show(health: HealthSyncUiState) {
        rule.setContent { Host(health) }
        rule.waitForIdle()
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xxhdpi")
    fun phone_bannerIsBelowTheLogButtonAndAboveTheCalendar() {
        show(HealthSyncUiState(HealthSyncStatus.NeverSynced))

        rule.onNodeWithTag("weight_hc_banner").assertIsDisplayed()
        assertTrue(bounds("weight_hc_banner").top >= bounds("weight_log_today_button").bottom)
        assertTrue(bounds("weight_hc_banner").bottom <= bounds("weight_calendar").top)
    }

    @Test
    @Config(qualifiers = "w900dp-h1200dp-xxhdpi")
    fun tablet_bannerIsUnderTheLogButtonInTheLeftColumn() {
        show(HealthSyncUiState(HealthSyncStatus.NeverSynced))

        val banner = bounds("weight_hc_banner")
        assertTrue(banner.top >= bounds("weight_log_today_button").bottom)
        assertTrue(banner.right <= bounds("weight_calendar").left)
    }

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xxhdpi")
    fun withoutHealthState_noBannerIsShown() {
        rule.setContent {
            AppTheme {
                WeightScreen(WeightUiState.Loading, {}, CircumferencesUiState.Loading, {}, timeZone = TimeZone.UTC)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag("weight_loading").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h2400dp-xxhdpi")
    fun syncedStatus_isShownInsideTheScreen() {
        show(HealthSyncUiState(HealthSyncStatus.Synced(Instant.parse("2026-10-22T10:18:00Z"), SyncAge.Minutes(12))))

        rule.onNodeWithTag("weight_hc_status").assertTextEquals("Sincronizado hace 12 min")
    }
}
