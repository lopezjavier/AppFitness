package io.github.lopezjavier.appfitness.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.feature.profile.ProfileRoute
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [AppViewModel] and the Profile tab to the stateless [App]. */
@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    viewModel: AppViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    App(
        modifier = modifier,
        features = features,
        isOffline = state.isOffline,
        rootRoute = state.rootRoute,
        avatarUrl = state.avatarUrl,
        onRetryProfile = { viewModel.onEvent(AppEvent.RetryProfile) },
        profile = { profileModifier, onOpenCatalog -> ProfileRoute(profileModifier, onOpenCatalog) },
        onOnboardingComplete = { viewModel.onEvent(AppEvent.OnboardingCompleted) },
    )
}
