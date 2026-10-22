package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_pill
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import org.jetbrains.compose.resources.stringResource

private val SkeletonPillWidth = Spacing.xl * 6
private val SkeletonPillHeight = Spacing.xl + Spacing.sm

/** The weekly rate under the current weight: signed value coloured by the goal, hidden when there is none. */
@Composable
internal fun WeeklyRatePill(trend: WeightTrendUiState) {
    when (trend) {
        WeightTrendUiState.Loading ->
            SkeletonBox(
                Modifier.size(SkeletonPillWidth, SkeletonPillHeight).testTag("weight_trend_loading"),
                AppShapes.full,
            )

        WeightTrendUiState.Error -> Unit

        is WeightTrendUiState.Content -> {
            val rate = trend.weeklyRate
            val value = rate.value ?: return
            TrendPill(
                text = stringResource(Res.string.weight_trend_pill, rateText(value)),
                tone = rate.tone.toTrendTone(),
                modifier = Modifier.testTag("weight_trend_pill"),
                direction = TrendPillDefaults.direction(value),
                containerColor = AppTheme.colors.surfaceRaised,
            )
        }
    }
}
