package io.github.lopezjavier.appfitness.feature.analytics.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.stats_error_message
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource

/** Lower-case tag suffix of a section: `stats_retry_<suffix>` and `stats_skeleton_<suffix>`. */
internal fun StatsSection.tagSuffix(): String = name.lowercase()

/**
 * One independent block: a skeleton of the final [skeletonHeight] while loading, a compact error with "Reintentar"
 * (`stats_retry_<section>`) on failure, [empty] when there is nothing, and [content] otherwise.
 */
@Composable
internal fun <T> StatsBlock(
    state: SectionState<T>,
    section: StatsSection,
    skeletonHeight: Dp,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    when (state) {
        SectionState.Loading -> Box(modifier.fillMaxWidth().testTag("stats_skeleton_${section.tagSuffix()}")) {
            SkeletonBox(Modifier.fillMaxWidth().height(skeletonHeight), AppShapes.lg)
        }

        SectionState.Empty -> empty()

        SectionState.Error -> ErrorState(
            message = stringResource(Res.string.stats_error_message),
            action = AppStateAction(stringResource(Res.string.action_retry), onRetry),
            modifier = modifier,
            actionTestTag = "stats_retry_${section.tagSuffix()}",
        )

        is SectionState.Content -> content(state.data)
    }
}

/** A titled card of the screen. */
@Composable
internal fun StatsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AppCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/** Secondary line of a card ("Sin series en este periodo"). */
@Composable
internal fun StatsHint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
}
