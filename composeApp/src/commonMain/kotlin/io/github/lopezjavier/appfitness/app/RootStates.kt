package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.designsystem.AppLogo
import io.github.lopezjavier.appfitness.designsystem.AppLogoDefaults
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StartHeroStage
import io.github.lopezjavier.appfitness.designsystem.StartShimmerBar
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.ambientBackdrop
import org.jetbrains.compose.resources.stringResource

/** Branded splash shown while the session or the profile resolves: the logo on its lit stage and a soft sweep. */
@Composable
internal fun RootLoading(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier.fillMaxSize().background(colors.surfaceBase).ambientBackdrop(colors.neonLime, colors.athleticCyan)
            .testTag("root_loading"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
        ) {
            StartHeroStage(Modifier.widthIn(max = WindowWidthClassDefaults.NarrowContentMaxWidth)) {
                AppLogo(width = AppLogoDefaults.Width)
            }
            StartShimmerBar()
        }
    }
}

/** The profile could not be read: message plus the required retry. */
@Composable
internal fun RootError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier.fillMaxSize().background(colors.surfaceBase).ambientBackdrop(colors.neonLime, colors.athleticCyan)
            .safeDrawingPadding().padding(horizontal = Spacing.screenMargin),
        contentAlignment = Alignment.Center,
    ) {
        ErrorState(
            message = stringResource(Res.string.root_error_message),
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            testTagPrefix = "root_error",
        )
    }
}
