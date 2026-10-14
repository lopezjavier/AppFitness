package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_back
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_goals_title
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.wip_footer
import io.github.lopezjavier.appfitness.composeapp.resources.wip_message
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.GhostButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WipPlaceholder
import org.jetbrains.compose.resources.stringResource

private const val SKELETON_BLOCKS = 3

@Composable
internal fun OnboardingLoading(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = Spacing.xl).testTag("onboarding_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth(LOADING_TITLE_FRACTION).height(Spacing.xl))
        repeat(SKELETON_BLOCKS) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * LOADING_BLOCK_UNITS), AppShapes.lg)
        }
    }
}

private const val LOADING_TITLE_FRACTION = 0.5f
private const val LOADING_BLOCK_UNITS = 3

@Composable
internal fun LoadFailure(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ErrorState(
            message = stringResource(Res.string.root_error_message),
            action = AppStateAction(stringResource(Res.string.state_retry), onRetry),
            testTagPrefix = "onboarding_error",
        )
    }
}

@Composable
internal fun ColumnScope.GoalsStub(onBack: () -> Unit) {
    Column(Modifier.weight(1f).fillMaxWidth().testTag("onboarding_goals_screen")) {
        Text(
            text = stringResource(Res.string.onboarding_goals_title),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = Spacing.lg).semantics { heading() },
        )
        WipPlaceholder(
            message = stringResource(Res.string.wip_message),
            footer = stringResource(Res.string.wip_footer),
            testTagPrefix = "onboarding_goals",
            modifier = Modifier.weight(1f),
        )
    }
    GhostButton(
        text = stringResource(Res.string.onboarding_back),
        onClick = onBack,
        modifier = Modifier.padding(bottom = Spacing.lg).testTag("onboarding_goals_back"),
    )
}
