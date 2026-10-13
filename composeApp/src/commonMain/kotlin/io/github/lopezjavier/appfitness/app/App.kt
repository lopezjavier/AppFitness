package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.navigationType
import io.github.lopezjavier.appfitness.designsystem.typeScale
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor

/** Root composable: theme plus the navigation shell. [modifier] reaches the root layout. */
@Composable
fun App(modifier: Modifier = Modifier, features: AppFeatures = AppFeatures()) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthClass = windowWidthClassFor(maxWidth)
        // No branch around AppScaffold: its NavHost and saved state must survive a live resize.
        AppTheme(typeScale = widthClass.typeScale) {
            AppScaffold(Modifier.fillMaxSize(), features = features, navigationType = widthClass.navigationType)
        }
    }
}
