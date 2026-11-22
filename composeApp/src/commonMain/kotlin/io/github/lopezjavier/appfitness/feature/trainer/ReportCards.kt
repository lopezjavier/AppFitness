package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.designsystem.AppCard

/** A plain read-only card for a one-line status (no title). */
@Composable
internal fun ReportNoteCard(tag: String, content: @Composable () -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag(tag)) { content() }
}
