package io.github.lopezjavier.appfitness.feature.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.notifications_toggle_error
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.designsystem.LocalAppMessages
import io.github.lopezjavier.appfitness.domain.model.ReminderKind
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful notification center: connects [NotificationCenterViewModel], recomputes on resume (a day may have passed
 * or something was logged elsewhere) and rejects with a haptic and a message when a switch could not be saved.
 */
@Composable
fun NotificationCenterRoot(
    onOpenReminder: (ReminderKind) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationCenterViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failed = stringResource(Res.string.notifications_toggle_error)
    val haptics = LocalAppHaptics.current
    val messages = LocalAppMessages.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                NotificationCenterEffect.ToggleFailed -> {
                    haptics.reject()
                    messages.show(failed)
                }
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(NotificationCenterEvent.Refresh) }
    NotificationCenterScreen(state, viewModel::onEvent, onOpenReminder, onBack, modifier)
}
