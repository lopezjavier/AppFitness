package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_chevron_right
import io.github.lopezjavier.appfitness.composeapp.resources.ic_info
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * One status line of an exercise card (R-51): [marker] (level dot or icon), [text] and an optional [trailing] action
 * on a `surfaceOverlay` block with the `md` shape, plus an optional [supporting] line under them. The whole row is a
 * 48 dp button announced with [onClickLabel] only when [onClick] is given. With a [trailing] slot the descendants are
 * not merged, so the action stays its own node. [primaryText] uses `textPrimary` (the discomfort level), otherwise
 * `textSecondary`. [testTag] lands on the root; [textTestTag] and [supportingTestTag] on the lines when set.
 */
@Composable
@Suppress("LongParameterList") // Slots plus the tags and semantics a row needs.
fun ExerciseStatusRow(
    text: String,
    marker: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    contentDescription: String? = null,
    primaryText: Boolean = false,
    textTestTag: String? = null,
    supportingTestTag: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceOverlay, AppShapes.md)
            .let {
                if (onClick != null) {
                    it.heightIn(min = Spacing.minTouchTarget)
                        .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    it
                }
            }
            .padding(horizontal = InlineNoteDefaults.Padding)
            .semantics(mergeDescendants = trailing == null) {
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .let { if (testTag != null) it.testTag(testTag) else it },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(InlineNoteDefaults.IconTextGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            marker()
            Text(
                text = text,
                style = AppTheme.typography.bodyMedium,
                color = if (primaryText) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                modifier = Modifier.weight(1f).let { if (textTestTag != null) it.testTag(textTestTag) else it },
            )
            trailing?.invoke()
        }
        if (supporting != null) {
            Text(
                text = supporting,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.sm)
                    .let { if (supportingTestTag != null) it.testTag(supportingTestTag) else it },
            )
        }
    }
}

/** Decorative info icon marker of a note or suggestion row. */
@Composable
fun ExerciseStatusInfoMarker() = StatusRowIcon(Res.drawable.ic_info)

/** Decorative chevron for a row that opens a sheet. */
@Composable
fun ExerciseStatusChevron() = StatusRowIcon(Res.drawable.ic_chevron_right)

@Composable
private fun StatusRowIcon(icon: DrawableResource) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AppTheme.colors.textSecondary,
        modifier = Modifier.size(InlineNoteDefaults.IconSize),
    )
}

@Preview
@Composable
private fun ExerciseStatusRowPreview() {
    AppTheme {
        ExerciseStatusRow(
            text = "Sugerencia: 60 kg × 9",
            marker = { ExerciseStatusInfoMarker() },
            onClick = {},
            trailing = { ExerciseStatusChevron() },
        )
    }
}
