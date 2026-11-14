package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults

/**
 * The adaptive grid of the Trainer hubs (R-91, C-106): columns of at least 300 dp, 16 dp gutters and 12 dp between
 * rows. The screen's `maxContentWidth` must be `HubContentMaxWidth`. Decision cards and headers use [fullSpanItem].
 */
@Composable
internal fun HubGrid(modifier: Modifier = Modifier, content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(WindowWidthClassDefaults.HubGridCellMinWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        content = content,
    )
}

/** An item that spans every column of the [HubGrid]. */
internal fun LazyGridScope.fullSpanItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
