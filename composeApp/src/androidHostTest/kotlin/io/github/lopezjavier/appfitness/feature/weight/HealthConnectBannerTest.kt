package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

private val SyncedAt = Instant.parse("2026-10-22T10:00:00Z")

/** US-26 banner states: one test per scenario state, with its single button. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
@Suppress("TooManyFunctions") // One method per state.
class HealthConnectBannerTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<HealthSyncEvent>()

    private fun show(status: HealthSyncStatus, online: Boolean = true, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
                AppTheme {
                    HealthConnectBanner(HealthSyncUiState(status, online), { events += it }, Modifier)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun statusText() = rule.onNodeWithTag("weight_hc_status")

    @Test
    fun neverSynced_showsSinSincronizarWithSyncButton() {
        show(HealthSyncStatus.NeverSynced)

        statusText().assertTextEquals("Sin sincronizar")
        rule.onNodeWithTag("weight_hc_sync_button").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun loading_showsASkeletonAndNoStatus() {
        show(HealthSyncStatus.Loading)

        rule.onNodeWithTag("weight_hc_skeleton").assertIsDisplayed()
        rule.onNodeWithTag("weight_hc_status").assertDoesNotExist()
    }

    @Test
    fun syncing_showsSincronizandoWithADisabledButton() {
        show(HealthSyncStatus.Syncing)

        statusText().assertTextEquals("Sincronizando...")
        rule.onNodeWithTag("weight_hc_sync_button").assertIsNotEnabled()
    }

    @Test
    fun syncedNow_showsSincronizadoAhoraWithADot() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Now))

        statusText().assertTextEquals("Sincronizado ahora")
        rule.onNodeWithTag("weight_hc_indicator").assertIsDisplayed()
    }

    @Test
    fun syncedTwelveMinutesAgo_showsMinutes() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Minutes(12)))

        statusText().assertTextEquals("Sincronizado hace 12 min")
    }

    @Test
    fun syncedHoursAndDaysAgo_showHoursAndDays() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Hours(3)))
        statusText().assertTextEquals("Sincronizado hace 3 h")
    }

    @Test
    fun syncedDaysAgo_showsDays() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Days(2)))

        statusText().assertTextEquals("Sincronizado hace 2 d")
    }

    @Test
    fun permissionNeeded_showsPermisoNecesarioWithPermitButton() {
        show(HealthSyncStatus.PermissionNeeded)

        statusText().assertTextEquals("Permiso necesario")
        rule.onNodeWithTag("weight_hc_permission_button").assertIsDisplayed().assertTextEqualsLabel("Permitir")
    }

    @Test
    fun notInstalled_offersInstall() {
        show(HealthSyncStatus.Unavailable(HealthAvailability.NotInstalled))

        statusText().assertTextEquals("Instala Health Connect")
        rule.onNodeWithTag("weight_hc_install_button").assertTextEqualsLabel("Instalar")
    }

    @Test
    fun updateRequired_offersUpdate() {
        show(HealthSyncStatus.Unavailable(HealthAvailability.UpdateRequired))

        statusText().assertTextEquals("Actualiza Health Connect")
        rule.onNodeWithTag("weight_hc_update_button").assertTextEqualsLabel("Actualizar")
    }

    @Test
    fun failed_showsErrorWithRetryButton() {
        show(HealthSyncStatus.Failed)

        statusText().assertTextEquals("No se pudo sincronizar")
        rule.onNodeWithTag("weight_hc_retry_button").assertTextEqualsLabel("Reintentar")
    }

    @Test
    fun offline_disablesTheButton() {
        show(HealthSyncStatus.NeverSynced, online = false)

        rule.onNodeWithTag("weight_hc_sync_button").assertIsNotEnabled()
    }

    @Test
    fun tappingTheButton_sendsTapEvent() {
        show(HealthSyncStatus.PermissionNeeded)

        rule.onNodeWithTag("weight_hc_permission_button").performClick()

        assertEquals(listOf<HealthSyncEvent>(HealthSyncEvent.Tap), events)
    }

    @Test
    fun onlyOneButtonIsComposedAtATime() {
        show(HealthSyncStatus.Failed)

        rule.onNodeWithTag("weight_hc_sync_button").assertDoesNotExist()
        rule.onNodeWithTag("weight_hc_permission_button").assertDoesNotExist()
    }

    // -- Accessibility ----------------------------------------------------------------------------------------------

    @Test
    fun button_hasAtLeast48dpTouchTarget() {
        show(HealthSyncStatus.NeverSynced)

        rule.onNodeWithTag("weight_hc_sync_button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun statusJustSynced_isAPoliteLiveRegion() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Now))

        assertNotNull(statusText().fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun statusWithTickedAge_isNotALiveRegion() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Minutes(1)))

        assertNull(statusText().fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun dot_exposesNoRoleOrDescription() {
        show(HealthSyncStatus.Failed)

        val config = rule.onNodeWithTag("weight_hc_indicator").fetchSemanticsNode().config
        assertNull(config.getOrNull(SemanticsProperties.Role))
        assertNull(config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(config.getOrNull(SemanticsProperties.Text))
    }

    @Test
    fun atLargeFontScale_textAndButtonAreFullyDisplayed() {
        show(HealthSyncStatus.Synced(SyncedAt, SyncAge.Minutes(59)), fontScale = 1.3f)

        statusText().assertIsDisplayed()
        rule.onNodeWithTag("weight_hc_sync_button").assertIsDisplayed()
        assertTrue(statusText().fetchSemanticsNode().layoutInfo.height > 0)
    }

    private fun SemanticsNodeInteraction.assertTextEqualsLabel(label: String) {
        val texts = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertEquals(listOf(label), texts)
    }
}
