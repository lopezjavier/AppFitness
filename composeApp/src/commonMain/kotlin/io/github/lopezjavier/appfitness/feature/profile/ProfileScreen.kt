package io.github.lopezjavier.appfitness.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_title
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

/**
 * Work-in-progress full-screen profile destination (ADR-22) until its epic replaces it.
 * [onOpenCatalog] is non-null only where the debug component catalog is available (local flavor).
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier, onOpenCatalog: (() -> Unit)? = null) {
    Column(modifier = modifier.testTag("profile_screen")) {
        if (onOpenCatalog != null) {
            SecondaryButton(
                text = stringResource(Res.string.catalog_title),
                onClick = onOpenCatalog,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screenMargin, end = Spacing.screenMargin, top = Spacing.lg)
                    .testTag("profile_catalog_button"),
            )
        }
        WipPlaceholder(
            message = stringResource(Res.string.wip_message),
            footer = stringResource(Res.string.wip_footer),
            testTagPrefix = "profile",
            modifier = Modifier.weight(1f),
        )
    }
}
