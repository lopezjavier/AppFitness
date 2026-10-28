package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.SyncPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HealthBannerSpecTest {
    private val at = Instant.parse("2026-10-22T10:00:00Z")

    private fun spec(status: HealthSyncStatus, online: Boolean = true) =
        healthBannerSpec(HealthSyncUiState(status, online))!!

    @Test
    fun loading_hasNoBanner() = assertNull(healthBannerSpec(HealthSyncUiState(HealthSyncStatus.Loading)))

    @Test
    fun neverSynced_isNeutralWithSync() {
        val spec = spec(HealthSyncStatus.NeverSynced)
        assertEquals(HealthTone.Neutral, spec.tone)
        assertEquals(HealthAction.Sync, spec.action)
    }

    @Test
    fun synced_isGreenWithSync() {
        val spec = spec(HealthSyncStatus.Synced(at, SyncAge.Now))
        assertEquals(HealthTone.Synced, spec.tone)
        assertEquals(HealthAction.Sync, spec.action)
    }

    @Test
    fun syncing_disablesTheButtonAndShowsLoading() {
        val spec = spec(HealthSyncStatus.Syncing)
        assertEquals(false, spec.actionEnabled)
        assertTrue(spec.actionLoading)
    }

    @Test
    fun permissionNeeded_isAmberWithPermit() {
        val spec = spec(HealthSyncStatus.PermissionNeeded)
        assertEquals(HealthTone.Attention, spec.tone)
        assertEquals(HealthAction.Permit, spec.action)
    }

    @Test
    fun notInstalled_offersInstall() =
        assertEquals(HealthAction.Install, spec(HealthSyncStatus.Unavailable(HealthAvailability.NotInstalled)).action)

    @Test
    fun updateRequired_offersUpdate() =
        assertEquals(HealthAction.Update, spec(HealthSyncStatus.Unavailable(HealthAvailability.UpdateRequired)).action)

    @Test
    fun failed_isRedWithRetry() {
        val spec = spec(HealthSyncStatus.Failed)
        assertEquals(HealthTone.Failed, spec.tone)
        assertEquals(HealthAction.Retry, spec.action)
    }

    @Test
    fun offline_disablesTheButton() =
        assertEquals(false, spec(HealthSyncStatus.NeverSynced, online = false).actionEnabled)

    @Test
    fun autoSyncFailed_isFailedWithRetry() {
        val spec = spec(HealthSyncStatus.AutoSyncFailed)
        assertEquals(HealthTone.Failed, spec.tone)
        assertEquals(HealthAction.Retry, spec.action)
    }

    @Test
    fun partialSync_isAttentionWithRetry() {
        val spec = spec(HealthSyncStatus.Synced(Instant.parse("2026-10-22T10:00:00Z"), SyncAge.Now, SyncPart.Watch))
        assertEquals(HealthTone.Attention, spec.tone)
        assertEquals(HealthAction.Retry, spec.action)
    }
}
