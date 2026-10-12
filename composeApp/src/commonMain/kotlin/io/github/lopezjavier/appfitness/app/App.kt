package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.designsystem.AppTheme

/** Root composable: theme plus the navigation shell. [modifier] reaches the root layout. */
@Composable
fun App(modifier: Modifier = Modifier, features: AppFeatures = AppFeatures()) {
    AppTheme {
        AppScaffold(modifier.fillMaxSize(), features = features)
    }
}
