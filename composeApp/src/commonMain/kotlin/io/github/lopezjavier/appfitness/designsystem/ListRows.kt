package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource

private const val HEADLINE_MAX_LINES = 2

/** Look of a disabled [NavigationRow]: not clickable, reduced emphasis. */
object NavigationRowDefaults {
    const val DISABLED_ALPHA = 0.5f
}

/**
 * Generic list row: optional [leading] and [trailing] slots around a [headline] (`bodyLarge`) and an optional
 * [supporting] line (`bodyMedium`, `textSecondary`). At least 48 dp high. With [onClick] the whole row is one
 * `Role.Button` target that reads as a single node; without it the row is plain content. The caller owns the
 * tag (pass it in [modifier]); [supportingTestTag] tags the supporting line;
 * [onClickLabel] is what a screen reader announces for the tap. [headlineTrailing] (default none) sits in the same
 * line right after the headline text, which shrinks first (an inline marker such as a favourite star).
 * [headlineStyle] (default `bodyLarge`) and [headlineMaxLines] (default 2) restyle the headline; [supportingSecondary]
 * is a second `bodyMedium` line below [supporting].
 */
@Composable
fun AppListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    supportingTestTag: String? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    headlineTrailing: (@Composable () -> Unit)? = null,
    headlineStyle: TextStyle? = null,
    headlineMaxLines: Int = HEADLINE_MAX_LINES,
    supportingSecondary: String? = null,
) {
    val clickable =
        if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .then(clickable)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            val headlineText: @Composable (Modifier) -> Unit = { textModifier ->
                Text(
                    text = headline,
                    style = headlineStyle ?: AppTheme.typography.bodyLarge,
                    color = AppTheme.colors.textPrimary,
                    maxLines = headlineMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = textModifier,
                )
            }
            if (headlineTrailing == null) {
                headlineText(Modifier)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    headlineText(Modifier.weight(1f, fill = false))
                    headlineTrailing()
                }
            }
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = if (supportingTestTag != null) Modifier.testTag(supportingTestTag) else Modifier,
                )
            }
            if (supportingSecondary != null) SingleLineSupporting(supportingSecondary)
        }
        trailing?.invoke()
    }
}

@Composable
private fun SingleLineSupporting(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A row that opens another screen (R-03): the [label], an optional current [value] in `textSecondary` and the
 * decorative chevron. At least 48 dp, one `Role.Button` node that reads "label, value".
 */
@Composable
fun NavigationRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
) {
    AppListRow(
        headline = label,
        modifier = if (enabled) modifier else modifier.alpha(NavigationRowDefaults.DISABLED_ALPHA),
        onClick = onClick,
        enabled = enabled,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (value != null) {
                    Text(text = value, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                }
                Icon(
                    painter = painterResource(Res.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = AppTheme.colors.textSecondary,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
        },
    )
}

/**
 * Title of a group of rows (`titleSection`, heading semantics) with an optional [count] in `textSecondary` after it
 * (plain text, not a pill). Named `ListSectionHeader` so it never clashes with the feature-private copies.
 */
@Composable
fun ListSectionHeader(title: String, modifier: Modifier = Modifier, count: Int? = null) {
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = title, style = AppTheme.typography.titleSection, color = AppTheme.colors.textPrimary)
        if (count != null) {
            Text(text = count.toString(), style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
        }
    }
}
