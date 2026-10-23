package io.github.lopezjavier.appfitness.feature.catalog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.catalog_section_stopwatch
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.StopwatchField
import org.jetbrains.compose.resources.stringResource

private const val DEMO_SECONDS = 90

/** The set stopwatch: starts and stops on tap and takes a typed mm:ss time (it does not tick in the catalog). */
@Composable
internal fun StopwatchSection() {
    var seconds by rememberSaveable { mutableStateOf(DEMO_SECONDS) }
    var running by rememberSaveable { mutableStateOf(false) }
    CatalogSection(stringResource(Res.string.catalog_section_stopwatch), "catalog_section_stopwatch") {
        AppCard(Modifier.fillMaxWidth()) {
            StopwatchField(
                elapsedSeconds = seconds,
                running = running,
                onToggle = { running = !running },
                onManualEntry = { seconds = it },
                modifier = Modifier.testTag("catalog_stopwatch_field"),
                testTagPrefix = "catalog_stopwatch",
            )
        }
    }
}
