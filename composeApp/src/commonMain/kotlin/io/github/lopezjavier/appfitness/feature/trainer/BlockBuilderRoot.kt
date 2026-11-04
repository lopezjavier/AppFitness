package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.lopezjavier.appfitness.designsystem.LocalAppHaptics
import io.github.lopezjavier.appfitness.domain.port.ConnectivityObserver
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful "Desde cero" flow (US-72, US-93): split, type per day and builder over [BlockBuilderViewModel]. [onSave]
 * leaves for the Trainer root after "Guardar bloque"; [onClose] leaves without saving (back from the first step or a
 * confirmed discard). System back goes one step back, and asks R-14 only from the builder when it holds picks.
 */
@Composable
fun BlockBuilderRoot(
    planId: String,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlockBuilderViewModel = koinViewModel { parametersOf(planId) },
    connectivity: ConnectivityObserver = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalAppHaptics.current
    val latestHaptics by rememberUpdatedState(haptics)
    val latestOnSaved by rememberUpdatedState(onSave)
    val latestOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BlockBuilderEffect.Confirm -> latestHaptics.confirm()
                BlockBuilderEffect.Reject -> latestHaptics.reject()
                BlockBuilderEffect.Saved -> latestOnSaved()
                BlockBuilderEffect.Close -> latestOnClose()
            }
        }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { viewModel.onEvent(BlockBuilderEvent.Back) },
    )
    BlockBuilderScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onOpenExercise = onOpenExercise,
        modifier = modifier,
    )
}
