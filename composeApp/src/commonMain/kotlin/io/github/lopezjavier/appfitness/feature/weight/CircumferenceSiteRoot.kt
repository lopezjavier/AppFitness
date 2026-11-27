package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.domain.model.CircumferenceSite
import io.github.lopezjavier.appfitness.domain.port.Clock
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful screen of one circumference [site] (T-324): connects its own [CircumferencesViewModel] (one per back-stack
 * entry, so the edit draft survives leaving the app) and reloads silently on resume. It leaves through [onBack] when
 * the site has no rows on arrival ([CircumferenceSiteUiState.Gone]) and once the site's last value is deleted (on
 * [CircumferencesEffect.Deleted], so the delete in flight is never cancelled; R-31).
 */
// The view model's one-shot effects are collected by the shared helper, so it receives them.
@Suppress("ViewModelForwarding")
@Composable
fun CircumferenceSiteRoot(
    site: CircumferenceSite,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRemindersChange: () -> Unit = {},
    viewModel: CircumferencesViewModel = koinViewModel(),
    clock: Clock = koinInject(),
) {
    val siteState = remember(viewModel, site) { viewModel.siteState(site) }
    val state by siteState.collectAsStateWithLifecycle()
    val latestOnBack by rememberUpdatedState(onBack)
    CollectCircumferenceEffects(
        effects = viewModel.effects,
        onRemindersChange = onRemindersChange,
        onDelete = {
            val current = siteState.value
            if (current is CircumferenceSiteUiState.Content && current.rows.isEmpty()) latestOnBack()
        },
    )
    LaunchedEffect(state) {
        if (state == CircumferenceSiteUiState.Gone) latestOnBack()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(CircumferencesEvent.Refresh) }
    CircumferenceSiteScreen(
        site = site,
        state = state,
        today = clock.today(),
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}
