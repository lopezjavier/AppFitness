package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppLogoDefaults
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
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

private const val ROOT_NAME_BAR_UNITS = 6
