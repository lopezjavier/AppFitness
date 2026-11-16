@file:Suppress("MatchingDeclarationName") // The file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview

/** Geometry of [SectionCard] (C-121). */
object SectionCardDefaults {
    val HeaderMinHeight = Spacing.minTouchTarget
    val TopPadding = Spacing.xs
    val HeaderToContentGap = Spacing.xs

    val ContentPadding =
        PaddingValues(
            start = Spacing.cardPadding,
            top = TopPadding,
            end = Spacing.cardPadding,
            bottom = Spacing.cardPadding,
        )
}

/**
 * A titled section inside one [AppCard]: a header row of at least 48 dp with the [title] (`titleMedium`, a heading)
 * and the optional [help] slot (a `HelpButton`) at the end, then the [content] 4 dp below. The card has 4 dp of top
 * padding. [testTag] lands on the card. It folds the feature-local summary cards (R-96).
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    help: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(
        modifier.fillMaxWidth().let { if (testTag != null) it.testTag(testTag) else it },
        contentPadding = SectionCardDefaults.ContentPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SectionCardDefaults.HeaderToContentGap)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = SectionCardDefaults.HeaderMinHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                help?.invoke()
            }
            content()
        }
    }
}

@Preview
@Composable
private fun SectionCardPreview() {
    AppTheme {
        SectionCard("Esta semana") {
            Text("Contenido", style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
