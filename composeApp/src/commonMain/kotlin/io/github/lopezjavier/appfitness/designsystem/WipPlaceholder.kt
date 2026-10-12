@file:Suppress("MatchingDeclarationName") // The file holds the WipPlaceholder composable and its Defaults object.

package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of [WipPlaceholder]. */
object WipPlaceholderDefaults {
    /** Enough cards to overflow a phone screen, so the scroll state is observable. */
    @Suppress("ktlint:standard:property-naming") // PascalCase kept to match the other defaults.
    const val SkeletonCount = 12
    val SkeletonHeight: Dp = 96.dp
}

private val SkeletonIndices = List(WipPlaceholderDefaults.SkeletonCount) { it }

/**
 * "Work in progress" screen body: title, message, shimmering skeleton cards and a footer, in a
 * scrollable list. Test tags: `<prefix>_wip_list`, `<prefix>_wip_title`, `<prefix>_wip_footer`.
 */
@Composable
fun WipPlaceholder(
    title: String,
    message: String,
    footer: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    // The list spacing is cardGap, so the extra gaps below top it up to blockGap.
    val extraBlockGap = Spacing.blockGap - Spacing.cardGap
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("${testTagPrefix}_wip_list"),
        state = state,
        contentPadding = PaddingValues(start = Spacing.screenMargin, end = Spacing.screenMargin, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            Column(
                modifier = Modifier.padding(bottom = extraBlockGap),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.testTag("${testTagPrefix}_wip_title").semantics { heading() },
                )
                Text(
                    text = message,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        items(SkeletonIndices) {
            SkeletonBox(Modifier.fillMaxWidth().height(WipPlaceholderDefaults.SkeletonHeight), AppShapes.lg)
        }
        item {
            Text(
                text = footer,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(top = extraBlockGap).testTag("${testTagPrefix}_wip_footer"),
            )
        }
    }
}

@Preview
@Composable
private fun WipPlaceholderPreview() {
    AppTheme {
        WipPlaceholder("Diario", "Sección en construcción.", "Más funciones próximamente", "diary")
    }
}
