package io.github.lopezjavier.appfitness.feature.diary

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.nav_diary
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/** Work-in-progress diary tab until its epic replaces it. */
@Composable
fun DiaryScreen(modifier: Modifier = Modifier) {
    WipPlaceholder(
        title = stringResource(Res.string.nav_diary),
        message = stringResource(Res.string.wip_message),
        footer = stringResource(Res.string.wip_footer),
        testTagPrefix = "diary",
        modifier = modifier.testTag("diary_screen"),
    )
}
