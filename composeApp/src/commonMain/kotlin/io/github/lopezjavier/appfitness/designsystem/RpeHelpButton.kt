package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_bullet_10
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_bullet_6
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_bullet_7
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_bullet_8
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_bullet_9
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_cd
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_intro
import io.github.lopezjavier.appfitness.composeapp.resources.help_rpe_title
import org.jetbrains.compose.resources.stringResource

/**
 * The "?" help of the RPE scale, shared by every place that asks for an RPE: an intro and one bullet per value
 * (10 down to 6), tagged `<testTag>_sheet_bullet_1..5`.
 */
@Composable
fun RpeHelpButton(testTag: String, modifier: Modifier = Modifier) {
    HelpButton(
        contentDescription = stringResource(Res.string.help_rpe_cd),
        title = stringResource(Res.string.help_rpe_title),
        content = HelpContent(
            paragraphs = listOf(stringResource(Res.string.help_rpe_intro)),
            bullets = listOf(
                stringResource(Res.string.help_rpe_bullet_10),
                stringResource(Res.string.help_rpe_bullet_9),
                stringResource(Res.string.help_rpe_bullet_8),
                stringResource(Res.string.help_rpe_bullet_7),
                stringResource(Res.string.help_rpe_bullet_6),
            ),
        ),
        testTag = testTag,
        modifier = modifier,
    )
}
