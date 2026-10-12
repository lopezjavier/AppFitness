package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.app_name
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Root composable. Placeholder until navigation arrives (T-021). */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme {
        Surface(modifier = modifier.fillMaxSize(), color = AppTheme.colors.surfaceBase) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = Spacing.screenMargin),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = AppTheme.typography.titleLarge,
                    modifier = Modifier.testTag("app_placeholder"),
                )
            }
        }
    }
}
