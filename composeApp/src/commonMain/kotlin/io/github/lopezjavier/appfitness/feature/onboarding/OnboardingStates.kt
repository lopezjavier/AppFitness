package io.github.lopezjavier.appfitness.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_check
import io.github.lopezjavier.appfitness.composeapp.resources.onboarding_goals_saved
import io.github.lopezjavier.appfitness.composeapp.resources.root_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

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

/** In-place confirmation shown after the goals are saved: a lime check and "Objetivos guardados". */
@Composable
internal fun GoalsSaved(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Image(
            imageVector = vectorResource(Res.drawable.ic_check),
            contentDescription = null,
            modifier = Modifier.size(Spacing.xl * 2),
            colorFilter = ColorFilter.tint(AppTheme.colors.neonLime),
        )
        Text(
            text = stringResource(Res.string.onboarding_goals_saved),
            style = AppTheme.typography.titleLarge,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("onboarding_goals_saved"),
        )
    }
}
