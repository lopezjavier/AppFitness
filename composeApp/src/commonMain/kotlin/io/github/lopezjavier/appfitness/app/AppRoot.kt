package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.feature.profile.ProfileEvent
import io.github.lopezjavier.appfitness.feature.profile.ProfileViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [AppViewModel] and [ProfileViewModel] to the stateless [App]. */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    viewModel: AppViewModel = koinViewModel(),
    profileViewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    App(
        modifier = modifier,
        features = features,
        isOffline = state.isOffline,
        rootRoute = state.rootRoute,
        avatarUrl = state.avatarUrl,
        onRetryProfile = { viewModel.onEvent(AppEvent.RetryProfile) },
        onSignOut = { profileViewModel.onEvent(ProfileEvent.SignOutConfirmed) },
    )
}
