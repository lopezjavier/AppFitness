package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful notification center: connects [NotificationCenterViewModel], recomputes on resume (a day may have passed
 * or something was logged elsewhere, or the switches changed in Preferencias).
 */
@Composable
fun NotificationCenterRoot(
    onOpenReminder: (ReminderKind) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationCenterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(NotificationCenterEvent.Refresh) }
    NotificationCenterScreen(state, viewModel::onEvent, onOpenReminder, onBack, modifier)
}
