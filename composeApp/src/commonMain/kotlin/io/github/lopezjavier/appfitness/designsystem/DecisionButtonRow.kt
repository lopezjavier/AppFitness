@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp

/** Geometry of [DecisionButtonRow] (C-104). */
object DecisionButtonRowDefaults {
    val ButtonHeight: Dp = AppButtonDefaults.PrimaryMinHeight
    val Gap: Dp = Spacing.md
    val TopPadding: Dp = Spacing.md

    fun acceptTag(prefix: String): String = "${prefix}_accept"

    fun discardTag(prefix: String): String = "${prefix}_discard"
}

/**
 * The one "decision" pair (R-94): a ghost [discardText] on the left and a primary [acceptText] on the right, both
 * 56 dp high, 12 dp apart and at least 12 dp below what is above. It stacks (primary on top) only when a label does
 * not fit on one line. Both are disabled while [busy] (the accept button shows the progress); [acceptEnabled] blocks
 * only the accept button. Tags `<testTagPrefix>_accept` and `<testTagPrefix>_discard`.
 */
@Composable
@Suppress("LongParameterList") // The two actions with their labels, state and tag prefix.
fun DecisionButtonRow(
    acceptText: String,
    discardText: String,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    acceptEnabled: Boolean = true,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = AppTheme.typography.titleMedium
    val discard: @Composable (Modifier) -> Unit = {
        GhostButton(
            text = discardText,
            onClick = onDiscard,
            modifier = it.heightIn(min = DecisionButtonRowDefaults.ButtonHeight)
                .testTag(DecisionButtonRowDefaults.discardTag(testTagPrefix)),
            enabled = !busy,
        )
    }
    val accept: @Composable (Modifier) -> Unit = {
        PrimaryButton(
            text = acceptText,
            onClick = onAccept,
            modifier = it.testTag(DecisionButtonRowDefaults.acceptTag(testTagPrefix)),
            enabled = !busy && acceptEnabled,
            loading = busy,
        )
    }
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = DecisionButtonRowDefaults.TopPadding)) {
        val slotPx = with(density) {
            ((maxWidth - DecisionButtonRowDefaults.Gap) / 2 - AppButtonDefaults.HorizontalPadding * 2).toPx()
        }
        val fits = listOf(discardText, acceptText).all {
            measurer.measure(it, style, softWrap = false).size.width <= slotPx
        }
        if (fits) {
            Row(horizontalArrangement = Arrangement.spacedBy(DecisionButtonRowDefaults.Gap)) {
                discard(Modifier.weight(1f))
                accept(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DecisionButtonRowDefaults.Gap)) {
                accept(Modifier.fillMaxWidth())
                discard(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview
@Composable
private fun DecisionButtonRowPreview() {
    AppTheme { DecisionButtonRow("Aceptar", "Descartar", {}, {}, "decision") }
}
