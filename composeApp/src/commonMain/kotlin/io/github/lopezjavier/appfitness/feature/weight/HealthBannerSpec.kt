package io.github.lopezjavier.appfitness.feature.weight

import io.github.lopezjavier.appfitness.domain.model.HealthAvailability

/** The colour family of the banner's status dot; the text always carries the state too. */
enum class HealthTone { Synced, Attention, Failed, Neutral }

/** The one trailing button of the banner. */
enum class HealthAction(val testTag: String) {
    Sync("weight_hc_sync_button"),
    Permit("weight_hc_permission_button"),
    Install("weight_hc_install_button"),
    Update("weight_hc_update_button"),
    Retry("weight_hc_retry_button"),
}

/** What the banner draws for a state: dot tone, the single button and whether it can be pressed. */
data class HealthBannerSpec(
    val status: HealthSyncStatus,
    val tone: HealthTone,
    val action: HealthAction,
    val actionEnabled: Boolean,
    val actionLoading: Boolean,
)

/** The mapping from the sync state to the banner; null while the state is loading (a skeleton is shown instead). */
fun healthBannerSpec(state: HealthSyncUiState): HealthBannerSpec? {
    val status = state.status
    val (tone, action) = when (status) {
        HealthSyncStatus.Loading -> return null
        HealthSyncStatus.NeverSynced, HealthSyncStatus.Syncing -> HealthTone.Neutral to HealthAction.Sync
        is HealthSyncStatus.Synced -> HealthTone.Synced to HealthAction.Sync
        HealthSyncStatus.PermissionNeeded -> HealthTone.Attention to HealthAction.Permit
        is HealthSyncStatus.Unavailable -> HealthTone.Attention to unavailableAction(status)
        HealthSyncStatus.Failed -> HealthTone.Failed to HealthAction.Retry
    }
    val syncing = status == HealthSyncStatus.Syncing
    return HealthBannerSpec(status, tone, action, actionEnabled = state.isOnline && !syncing, actionLoading = syncing)
}

private fun unavailableAction(status: HealthSyncStatus.Unavailable) =
    if (status.availability == HealthAvailability.UpdateRequired) HealthAction.Update else HealthAction.Install
