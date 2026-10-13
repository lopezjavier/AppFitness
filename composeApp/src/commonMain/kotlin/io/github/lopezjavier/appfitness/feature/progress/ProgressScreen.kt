package io.github.lopezjavier.appfitness.feature.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/** Work-in-progress progress tab until its epic replaces it. */
@Composable
fun ProgressScreen(modifier: Modifier = Modifier, contentBottomClearance: Dp = Spacing.none) {
    WipPlaceholder(
        message = stringResource(Res.string.wip_message),
        footer = stringResource(Res.string.wip_footer),
        testTagPrefix = "progress",
        contentBottomClearance = contentBottomClearance,
        modifier = modifier.testTag("progress_screen"),
    )
}
