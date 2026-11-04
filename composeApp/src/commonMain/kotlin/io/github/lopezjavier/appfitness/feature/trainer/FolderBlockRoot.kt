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
 * Stateful "Desde una carpeta" (US-93) over [FolderBlockViewModel]. [onSave] leaves after "Guardar bloque"; [onClose]
 * leaves without saving (back with nothing edited or a confirmed discard). System back asks R-14 only with edits.
 */
@Composable
fun FolderBlockRoot(
    planId: String,
    folderId: String,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderBlockViewModel = koinViewModel { parametersOf(planId, folderId) },
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
                FolderBlockEffect.Confirm -> latestHaptics.confirm()
                FolderBlockEffect.Reject -> latestHaptics.reject()
                FolderBlockEffect.Saved -> latestOnSaved()
                FolderBlockEffect.Close -> latestOnClose()
            }
        }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { viewModel.onEvent(FolderBlockEvent.Back) },
    )
    FolderBlockScreen(
        state = state,
        isOnline = connectivity.isOnline,
        onEvent = viewModel::onEvent,
        onOpenExercise = onOpenExercise,
        modifier = modifier,
    )
}
