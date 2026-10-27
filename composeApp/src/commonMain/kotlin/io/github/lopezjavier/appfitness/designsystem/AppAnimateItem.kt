package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animates a lazy-list item when it is added, removed or reordered (R-23). With reduced motion
 * ([LocalAnimationsEnabled] false) the modifier is a no-op, so the list snaps to its final state.
 * The item must have a stable `key`.
 */
@Composable
fun LazyItemScope.appAnimateItem(modifier: Modifier = Modifier): Modifier =
    if (LocalAnimationsEnabled.current) modifier.animateItem() else modifier

/** [appAnimateItem] for a `LazyVerticalGrid` item. */
@Composable
fun LazyGridItemScope.appAnimateItem(modifier: Modifier = Modifier): Modifier =
    if (LocalAnimationsEnabled.current) modifier.animateItem() else modifier
