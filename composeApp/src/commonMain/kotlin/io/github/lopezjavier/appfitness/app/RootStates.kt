package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_title
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.AppLogoDefaults
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/** Skeleton shown while the session or the profile resolves: a logo-sized circle and a name bar, never Welcome. */
@Composable
internal fun RootLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().testTag("root_loading"), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            SkeletonBox(Modifier.size(AppLogoDefaults.Size), AppShapes.full)
            SkeletonBox(Modifier.width(Spacing.xl * ROOT_NAME_BAR_UNITS).height(Spacing.xl))
        }
    }
}

/** The profile could not be read: message plus the required retry. */
@Composable
internal fun RootError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.root_error_message),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "root_error",
        )
    }
}

/** Stub destination for a signed-in user whose onboarding is not completed, until the onboarding epic. */
@Composable
internal fun OnboardingPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().safeDrawingPadding().testTag("onboarding_screen")) {
        Text(
            text = stringResource(Res.string.onboarding_title),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .semantics { heading() },
        )
        WipPlaceholder(
            message = stringResource(Res.string.wip_message),
            footer = stringResource(Res.string.wip_footer),
            testTagPrefix = "onboarding",
            modifier = Modifier.weight(1f),
        )
    }
}

private const val ROOT_NAME_BAR_UNITS = 6
