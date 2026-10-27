package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role

/**
 * A text-only choice row of a picker sheet body (for example the folder targets): full width, at least 48 dp. A
 * disabled row is dimmed and inert; a [destructive] one reads in the error colour. Use [ActionRow] inside an
 * [AppActionSheet] when the row has an icon.
 */
@Composable
fun SheetActionRow(
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = AppTheme.colors
    val color =
        when {
            !enabled -> colors.ghostData
            destructive -> colors.error
            else -> colors.textPrimary
        }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, style = AppTheme.typography.bodyLarge, color = color)
    }
}
