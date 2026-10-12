package io.github.lopezjavier.appfitness.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Root composable. Placeholder until the design system (T-020) and navigation (T-021). */
@Composable
fun App(modifier: Modifier = Modifier) {
    MaterialTheme {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Brand name, not translated; Compose string resources arrive in T-020.
            Text(text = "AppFitness", modifier = Modifier.testTag("app_placeholder"))
        }
    }
}
