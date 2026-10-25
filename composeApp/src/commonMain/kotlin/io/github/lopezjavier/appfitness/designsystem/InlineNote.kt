@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import org.jetbrains.compose.resources.painterResource

/** Sizes of an [InlineNote]. */
object InlineNoteDefaults {
    val IconSize: Dp = Spacing.xl
    val Padding: Dp = Spacing.md
    val IconTextGap: Dp = Spacing.sm
}

/**
 * Subtle hint inside a form: decorative info icon and [text] (`bodyMedium`, `textSecondary`) on a `surfaceOverlay`
 * block with the `md` shape. Not interactive; the text is the only spoken content. [testTag] lands on the root.
 */
@Composable
fun InlineNote(text: String, modifier: Modifier = Modifier, testTag: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceOverlay, AppShapes.md)
            .padding(InlineNoteDefaults.Padding)
            .semantics(mergeDescendants = true) {}
            .let { if (testTag != null) it.testTag(testTag) else it },
        horizontalArrangement = Arrangement.spacedBy(InlineNoteDefaults.IconTextGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_info),
            contentDescription = null,
            tint = AppTheme.colors.textSecondary,
            modifier = Modifier.size(InlineNoteDefaults.IconSize),
        )
        Text(text = text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
    }
}

@Preview
@Composable
private fun InlineNotePreview() {
    AppTheme {
        InlineNote("Estimado por IA · revisa los valores")
    }
}
