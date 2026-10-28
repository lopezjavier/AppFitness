package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import org.jetbrains.compose.resources.painterResource

private const val ROW_TINT_ALPHA = 0.16f

/**
 * One option of a coloured, ordered scale (RPE, discomfort level): a tinted row with a leading 8 dp marker bar in
 * [color], the [label] in `textPrimary` (colour is never the only cue; long labels wrap) and a check when [selected].
 * A single-select `Role.RadioButton` of at least 48 dp; [contentDescription] is what TalkBack reads.
 */
@Suppress("LongParameterList") // Row content, state, tag and spoken description of one option.
@Composable
internal fun ScaleOptionRow(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = AppTheme.typography.titleMedium,
) {
    val colors = AppTheme.colors
    val shape = AppShapes.md
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(color.copy(alpha = ROW_TINT_ALPHA).compositeOver(colors.surfaceOverlay))
            .then(if (selected) Modifier.border(AppBorder.focus, color, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Spacing.sm).fillMaxHeight().background(color))
        Text(
            text = label,
            style = textStyle.copy(fontWeight = if (selected) FontWeight.SemiBold else textStyle.fontWeight),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        if (selected) {
            // Decorative: the selected state is conveyed by the selectable semantics.
            Icon(
                painter = painterResource(Res.drawable.ic_check),
                contentDescription = null,
                tint = color,
                modifier = Modifier.padding(end = Spacing.lg).size(Spacing.xl),
            )
        }
    }
}
