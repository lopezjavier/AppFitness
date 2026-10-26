package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_cloud_off
import io.github.lopezjavier.appfitness.composeapp.resources.offline_state_message
import io.github.lopezjavier.appfitness.composeapp.resources.offline_state_title
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource

/** Tag of [OfflineState]. */
const val OFFLINE_STATE_TAG = "offline_state"

/**
 * What a screen shows when a load failed for lack of connection (R-30): the `cloud_off` icon, "Sin conexión" and
 * "Conéctate para ver estos datos." in the look of an empty state, with no button. It retries by itself: [onReload] is
 * called once on every offline to online transition of [isOnline] (see [OfflineRetry]). A polite live region, so a
 * screen reader announces it without stealing focus.
 */
@Composable
fun OfflineState(isOnline: Flow<Boolean>, onReload: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnReload = rememberUpdatedState(onReload)
    LaunchedEffect(isOnline) { OfflineRetry.collect(isOnline) { currentOnReload.value() } }
    EmptyState(
        message = stringResource(Res.string.offline_state_message),
        title = stringResource(Res.string.offline_state_title),
        icon = Res.drawable.ic_cloud_off,
        testTagPrefix = OFFLINE_STATE_TAG,
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    )
}
