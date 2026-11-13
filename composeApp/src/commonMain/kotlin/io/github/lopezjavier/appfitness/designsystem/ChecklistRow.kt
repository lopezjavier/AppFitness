package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.checklist_checked
import io.github.lopezjavier.appfitness.composeapp.resources.checklist_unchecked
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val TITLE_MAX_LINES = 2
private const val DISABLED_ALPHA = 0.5f

/**
 * Row of a checklist (T-208): a checkbox, a [title] (two lines at most) and a [supporting] line that wraps (the reason
 * of a proposed change). The whole row is one `Role.Checkbox` target of at least 48 dp that reads as one node with
 * the state "Marcado" / "Sin marcar"; the glyph and the border differ per state, so the state is never colour alone.
 * Stateless: the caller owns [checked] and [testTag].
 */
@Composable
fun ChecklistRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    testTag: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val state = stringResource(if (checked) Res.string.checklist_checked else Res.string.checklist_unchecked)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .testTag(testTag)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        CheckGlyph(checked, enabled)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CheckGlyph(checked: Boolean, enabled: Boolean) {
    val colors = AppTheme.colors
    val container = if (checked) colors.neonLime else colors.surfaceOverlay
    val glyph = if (checked) colors.onNeonLime else colors.textTertiary
    Box(
        Modifier
            .padding(top = Spacing.xs)
            .size(Spacing.xl)
            .clip(AppShapes.sm)
            .background(container)
            .border(BorderStroke(AppBorder.thin, colors.borderStrong), AppShapes.sm)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = glyph,
                modifier = Modifier.size(Spacing.lg),
            )
        }
    }
}
