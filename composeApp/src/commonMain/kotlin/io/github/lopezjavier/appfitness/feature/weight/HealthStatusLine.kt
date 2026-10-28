package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.health_action_install
import io.github.lopezjavier.appfitness.composeapp.resources.health_action_permit
import io.github.lopezjavier.appfitness.composeapp.resources.health_action_sync
import io.github.lopezjavier.appfitness.composeapp.resources.health_action_update
import io.github.lopezjavier.appfitness.composeapp.resources.health_auto_failed
import io.github.lopezjavier.appfitness.composeapp.resources.health_failed
import io.github.lopezjavier.appfitness.composeapp.resources.health_install_needed
import io.github.lopezjavier.appfitness.composeapp.resources.health_never_synced
import io.github.lopezjavier.appfitness.composeapp.resources.health_partial_watch
import io.github.lopezjavier.appfitness.composeapp.resources.health_partial_weight
import io.github.lopezjavier.appfitness.composeapp.resources.health_permission_needed
import io.github.lopezjavier.appfitness.composeapp.resources.health_synced_days
import io.github.lopezjavier.appfitness.composeapp.resources.health_synced_hours
import io.github.lopezjavier.appfitness.composeapp.resources.health_synced_minutes
import io.github.lopezjavier.appfitness.composeapp.resources.health_synced_now
import io.github.lopezjavier.appfitness.composeapp.resources.health_syncing
import io.github.lopezjavier.appfitness.composeapp.resources.health_update_needed
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.HealthAvailability
import io.github.lopezjavier.appfitness.domain.model.SyncPart
import org.jetbrains.compose.resources.stringResource

private val SkeletonLineHeight = Spacing.xl

/**
 * The Health Connect status of "Peso actual" (US-26): a status dot, the status text and one button ("Sincronizar" and
 * its siblings). While the state loads a skeleton line is drawn, so "Sin sincronizar" never flashes.
 */
@Composable
fun HealthStatusLine(state: HealthSyncUiState, onEvent: (HealthSyncEvent) -> Unit, modifier: Modifier = Modifier) {
    val spec = healthBannerSpec(state)
    if (spec == null) {
        SkeletonBox(
            modifier.fillMaxWidth().height(SkeletonLineHeight).testTag("weight_hc_skeleton"),
            AppShapes.base,
        )
        return
    }
    Column(
        modifier.fillMaxWidth().testTag("weight_hc_banner"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(toneColor(spec.tone))
            StatusText(spec)
        }
        GhostButton(
            text = actionLabel(spec.action),
            onClick = { onEvent(HealthSyncEvent.Tap) },
            modifier = Modifier.testTag(spec.action.testTag),
            enabled = spec.actionEnabled,
            loading = spec.actionLoading,
        )
    }
}

/** Decorative: the text carries the state, so the dot exposes no semantics. */
@Composable
private fun StatusDot(color: Color) {
    Box(
        Modifier
            .size(Spacing.sm)
            .clip(AppShapes.full)
            .background(color)
            .testTag("weight_hc_indicator")
            .clearAndSetSemantics {},
    )
}

/**
 * The status is a polite live region so "Sincronizando…" -> "Sincronizado ahora" is announced; once the age ticks on
 * ("hace 1 min") the region is off, so the minute ticker never re-announces.
 */
@Composable
private fun StatusText(spec: HealthBannerSpec) {
    val status = spec.status
    val announce = status !is HealthSyncStatus.Synced || status.age == SyncAge.Now
    Text(
        text = statusLabel(status),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textPrimary,
        modifier = Modifier
            .semantics { if (announce) liveRegion = LiveRegionMode.Polite }
            .testTag("weight_hc_status"),
    )
}

@Composable
private fun toneColor(tone: HealthTone): Color = when (tone) {
    HealthTone.Synced -> AppTheme.colors.electricGreen
    HealthTone.Attention -> AppTheme.colors.energyAmber
    HealthTone.Failed -> AppTheme.colors.error
    HealthTone.Neutral -> AppTheme.colors.ghostData
}

@Composable
private fun statusLabel(status: HealthSyncStatus): String = when (status) {
    HealthSyncStatus.Loading, HealthSyncStatus.NeverSynced -> stringResource(Res.string.health_never_synced)

    HealthSyncStatus.Syncing -> stringResource(Res.string.health_syncing)

    is HealthSyncStatus.Synced -> when (status.failedPart) {
        SyncPart.Watch -> stringResource(Res.string.health_partial_watch)
        SyncPart.Weight -> stringResource(Res.string.health_partial_weight)
        null -> syncedAgeLabel(status.age)
    }

    HealthSyncStatus.PermissionNeeded -> stringResource(Res.string.health_permission_needed)

    is HealthSyncStatus.Unavailable -> if (status.availability == HealthAvailability.UpdateRequired) {
        stringResource(Res.string.health_update_needed)
    } else {
        stringResource(Res.string.health_install_needed)
    }

    HealthSyncStatus.Failed -> stringResource(Res.string.health_failed)

    HealthSyncStatus.AutoSyncFailed -> stringResource(Res.string.health_auto_failed)
}

@Composable
private fun syncedAgeLabel(age: SyncAge): String = when (age) {
    SyncAge.Now -> stringResource(Res.string.health_synced_now)
    is SyncAge.Minutes -> stringResource(Res.string.health_synced_minutes, age.value)
    is SyncAge.Hours -> stringResource(Res.string.health_synced_hours, age.value)
    is SyncAge.Days -> stringResource(Res.string.health_synced_days, age.value)
}

@Composable
private fun actionLabel(action: HealthAction): String = stringResource(
    when (action) {
        HealthAction.Sync -> Res.string.health_action_sync
        HealthAction.Permit -> Res.string.health_action_permit
        HealthAction.Install -> Res.string.health_action_install
        HealthAction.Update -> Res.string.health_action_update
        HealthAction.Retry -> Res.string.action_retry
    },
)
