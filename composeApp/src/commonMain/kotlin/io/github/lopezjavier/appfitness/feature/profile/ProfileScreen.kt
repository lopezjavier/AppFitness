package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.nav_profile
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/** Work-in-progress profile tab until its epic replaces it. */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    WipPlaceholder(
        title = stringResource(Res.string.nav_profile),
        message = stringResource(Res.string.wip_message),
        footer = stringResource(Res.string.wip_footer),
        testTagPrefix = "profile",
        modifier = modifier.testTag("profile_screen"),
    )
}
