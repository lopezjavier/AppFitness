@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds the component and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_close
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Sizes of an [InlineNote]. */
object InlineNoteDefaults {
    val IconSize: Dp = Spacing.xl
    val Padding: Dp = Spacing.md
    val IconTextGap: Dp = Spacing.sm
}

/**
 * Subtle hint inside a form: decorative info icon and [text] (`bodyMedium`, `textSecondary`) on a `surfaceOverlay`
 * block with the `md` shape. Not interactive unless [onClick] is given (then a 48 dp button announced with
 * [onClickLabel], e.g. a shown note that opens its editor); the text is the only spoken content. [testTag] lands on
 * the root. With [onDismiss] a trailing close button (48 dp, "Cerrar", tagged [dismissTestTag]) lets the user remove a
 * note that shows once (R-52); the text stays the first spoken content.
 */
@Composable
fun InlineNote(
    text: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissTestTag: String = "inline_note_close",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(nestedContainerColor(), AppShapes.md)
            .let {
                if (onClick != null) {
                    it.heightIn(min = Spacing.minTouchTarget)
                        .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    it
                }
            }
            .padding(InlineNoteDefaults.Padding)
            .semantics(mergeDescendants = onDismiss == null) {}
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
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            AppIconButton(
                contentDescription = stringResource(Res.string.action_close),
                icon = Res.drawable.ic_close,
                testTag = dismissTestTag,
                onClick = onDismiss,
                tint = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Preview
@Composable
private fun InlineNotePreview() {
    AppTheme {
        InlineNote("Estimado por IA · revisa los valores")
    }
}

@Preview
@Composable
private fun InlineNoteDismissiblePreview() {
    AppTheme {
        InlineNote("Una nota que se puede cerrar", onDismiss = {})
    }
}
