package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.navigationType
import io.github.lopezjavier.appfitness.designsystem.typeScale
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.feature.auth.WelcomeRoute

/**
 * Root composable: theme plus the destination chosen by [rootRoute]. [modifier] reaches the root layout;
 * [isOffline] shows the "Sin conexión" banner on every destination; [avatarUrl] is the top bar photo;
 * [onRetryProfile] and [onSignOut] are the root actions. [welcome] is the Welcome destination (stateful by default).
 *
 * The default [rootRoute] is [RootRoute.Main], so previews and shell tests need no session.
 */
@Composable
fun App(
    modifier: Modifier = Modifier,
    features: AppFeatures = AppFeatures(),
    isOffline: Boolean = false,
    rootRoute: RootRoute = RootRoute.Main,
    avatarUrl: String? = null,
    onRetryProfile: () -> Unit = {},
    onSignOut: () -> Unit = {},
    welcome: @Composable () -> Unit = { WelcomeRoute(showTestLogin = features.testLogin != null) },
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = windowWidthClassFor(maxWidth)
        AppTheme(typeScale = widthClass.typeScale) {
            // The branch is on the route only: leaving Main disposes the scaffold and its NavHostController, which
            // resets every tab back stack. Inside Main there is no branch, so its NavHost and saved state survive
            // a live resize.
            when (rootRoute) {
                RootRoute.Main ->
                    AppScaffold(
                        Modifier.fillMaxSize(),
                        features = features,
                        navigationType = widthClass.navigationType,
                        isOffline = isOffline,
                        avatarUrl = avatarUrl,
                        onSignOut = onSignOut,
                    )

                else ->
                    RootFrame(isOffline) {
                        when (rootRoute) {
                            RootRoute.Loading -> RootLoading()
                            RootRoute.Welcome -> welcome()
                            RootRoute.Onboarding -> OnboardingPlaceholder()
                            RootRoute.ProfileError -> RootError(onRetry = onRetryProfile)
                            RootRoute.Main -> Unit
                        }
                    }
            }
        }
    }
}

/** Frame of the destinations outside the shell: `surfaceBase` with the offline banner on top (planning/05 §6). */
@Composable
private fun RootFrame(isOffline: Boolean, content: @Composable () -> Unit) {
    val bannerInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    Column(Modifier.fillMaxSize().background(AppTheme.colors.surfaceBase)) {
        if (isOffline) {
            OfflineBanner(Modifier.fillMaxWidth().windowInsetsPadding(bannerInsets), testTagPrefix = "offline_banner")
        }
        // The banner already took the top inset, so the destination must not pad for it again.
        Column(
            Modifier.weight(
                1f,
            ).fillMaxWidth().then(if (isOffline) Modifier.consumeWindowInsets(bannerInsets) else Modifier),
        ) {
            content()
        }
    }
}
