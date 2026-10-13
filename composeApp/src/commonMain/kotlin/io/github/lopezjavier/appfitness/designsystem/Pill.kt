@file:Suppress("MatchingDeclarationName")
// MatchingDeclarationName: the file holds PillDefaults and the PillContainer composable.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Geometry shared by every pill (the weekly [TrendPill] and the ring status). */
internal object PillDefaults {
    val Shape = AppShapes.full
    val MinHeight: Dp = 32.dp
    val HorizontalPadding: Dp = Spacing.md
    val VerticalPadding: Dp = Spacing.xs
    val ItemSpacing: Dp = Spacing.xs

    fun containerColor(appColors: AppColors): Color = appColors.surfaceOverlay
}

/** The visual pill primitive: overlay fill, full radius, md/xs padding and a 32 dp minimum height. */
@Composable
internal fun PillContainer(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
        modifier
            .heightIn(min = PillDefaults.MinHeight)
            .clip(PillDefaults.Shape)
            .background(PillDefaults.containerColor(AppTheme.colors))
            .padding(horizontal = PillDefaults.HorizontalPadding, vertical = PillDefaults.VerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(PillDefaults.ItemSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
