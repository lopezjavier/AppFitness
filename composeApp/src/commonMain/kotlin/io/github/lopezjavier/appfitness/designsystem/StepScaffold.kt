package io.github.lopezjavier.appfitness.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

private const val SLIDE_MILLIS = 300
private val MaxContentWidth: Dp = WindowWidthClassDefaults.FormContentMaxWidth

/**
 * Template of a step-by-step flow that sits outside `AppScreen` (onboarding): a fixed header with the step
 * [indicator], then, per step, the [title], the [helper] sentence and the scrolling [content], and a [footer] pinned
 * at the bottom (above the keyboard; its height is published for the message host, like `AppScreen`).
 *
 * [step] is 1-based; changing it slides title, helper and content (forward from the end, back from the start,
 * RTL-aware) and never the footer. With [LocalAnimationsEnabled] false the step swaps without motion. Focus moves to
 * the title of the new step. [title], [helper] and [content] receive the step they render so the outgoing step keeps
 * its own text while it leaves.
 */
@Suppress("LongParameterList") // The template slots: indicator, title, helper, content and footer.
@Composable
fun StepScaffold(
    step: Int,
    indicator: String,
    indicatorDescription: String,
    title: @Composable (Int) -> String,
    helper: @Composable (Int) -> String,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "step",
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    val animate = LocalAnimationsEnabled.current
    val focusTitle = remember { FocusRequester() }
    LaunchedEffect(step) { focusTitle.requestFocus() }
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .widthIn(max = MaxContentWidth)
            .padding(horizontal = Spacing.screenMargin),
    ) {
        Text(
            text = indicator,
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.lg)
                .semantics { contentDescription = indicatorDescription }
                .testTag("${testTagPrefix}_step_indicator"),
        )
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { stepTransition(animate) },
            label = "step",
        ) { shown ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = title(shown),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = Spacing.sm)
                        .then(if (shown == step) Modifier.focusRequester(focusTitle).focusable() else Modifier)
                        .semantics { heading() }
                        .testTag("${testTagPrefix}_step_title"),
                )
                Text(
                    text = helper(shown),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("${testTagPrefix}_step_helper"),
                )
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.blockGap)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.Start,
                ) { content(shown) }
            }
        }
        PinnedStepFooter(testTagPrefix, footer)
    }
}

private fun AnimatedContentTransitionScope<Int>.stepTransition(animate: Boolean): ContentTransform {
    if (!animate) return EnterTransition.None togetherWith ExitTransition.None
    val direction = if (targetState > initialState) {
        AnimatedContentTransitionScope.SlideDirection.Start
    } else {
        AnimatedContentTransitionScope.SlideDirection.End
    }
    val spec = tween<IntOffset>(SLIDE_MILLIS)
    return (slideIntoContainer(direction, spec) togetherWith slideOutOfContainer(direction, spec))
        .using(SizeTransform(clip = true))
}

@Composable
private fun PinnedStepFooter(testTagPrefix: String, footer: @Composable () -> Unit) {
    val state = LocalPinnedFooter.current
    val density = LocalDensity.current
    DisposableEffect(state) { onDispose { state?.height = Spacing.none } }
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(bottom = Spacing.lg)
            .onSizeChanged { size -> state?.height = with(density) { size.height.toDp() } }
            .testTag("${testTagPrefix}_footer"),
    ) { footer() }
}
