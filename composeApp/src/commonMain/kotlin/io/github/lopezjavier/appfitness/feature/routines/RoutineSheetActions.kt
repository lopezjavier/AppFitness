package io.github.lopezjavier.appfitness.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.lopezjavier.appfitness.designsystem.AppBottomSheet
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.Spacing

/** Minimum height of a sheet action row (a 48 dp touch target). */
private val ActionRowMinHeight = Spacing.minTouchTarget

/**
 * Bottom sheet listing [title] and a column of [SheetActionRow]s (the routine row menu and the builder row menu).
 * The root is tagged [testTagPrefix].
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class) // The shared sheet exposes a Material SheetState default.
fun ActionsSheet(
    title: String,
    onDismiss: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, testTagPrefix = testTagPrefix) {
        Column(
            Modifier.padding(horizontal = Spacing.screenMargin).padding(bottom = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = Spacing.sm).semantics { heading() },
            )
            content()
        }
    }
}

/** One action of an [ActionsSheet]: a full-width row of at least 48 dp. A disabled row is dimmed and inert. */
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
            .heightIn(min = ActionRowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = label, style = AppTheme.typography.bodyLarge, color = color)
    }
}
