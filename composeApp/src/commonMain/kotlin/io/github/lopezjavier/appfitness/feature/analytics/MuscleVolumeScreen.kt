package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_all
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_caption
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_help_body
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_help_cd
import io.github.lopezjavier.appfitness.composeapp.resources.muscle_volume_title
import io.github.lopezjavier.appfitness.composeapp.resources.strength_empty_cta
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.rememberHelpTopBarAction
import io.github.lopezjavier.appfitness.feature.exercises.label
import io.github.lopezjavier.appfitness.feature.exercises.tagName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SkeletonCardHeight = Spacing.xl * 5

/** Stateful weekly sets per group (connects [MuscleVolumeViewModel]); [onStartWorkout] leads to Entreno when empty. */
@Composable
fun MuscleVolumeRoot(
    onStartWorkout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MuscleVolumeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MuscleVolumeScreen(state, viewModel::onEvent, onStartWorkout, onBack, modifier)
}

/**
 * "Series por grupo muscular" (US-15, US-145): weekly sets of each muscle group for the last 8 weeks; a chip
 * selects one group to see its evolution alone.
 */
@Composable
fun MuscleVolumeScreen(
    state: MuscleVolumeUiState,
    onEvent: (MuscleVolumeEvent) -> Unit,
    onStartWorkout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val help = rememberHelpTopBarAction(
        contentDescription = stringResource(Res.string.muscle_volume_help_cd),
        title = stringResource(Res.string.muscle_volume_title),
        body = stringResource(Res.string.muscle_volume_help_body),
        testTag = "muscle_sets_help",
    )
    AppScreen(
        title = stringResource(Res.string.muscle_volume_title),
        modifier = modifier.testTag("muscle_sets_screen"),
        onBack = onBack,
        actions = listOf(help),
        testTagPrefix = "muscle_sets",
    ) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
                MuscleVolumeUiState.Loading -> MuscleVolumeLoading()

                MuscleVolumeUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Res.drawable.ic_bar_chart,
                        message = stringResource(Res.string.muscle_volume_empty_message),
                        action = AppStateAction(stringResource(Res.string.strength_empty_cta), onStartWorkout),
                        testTagPrefix = "muscle_sets_empty",
                    )
                }

                MuscleVolumeUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.muscle_volume_error_message),
                        action = AppStateAction(stringResource(Res.string.action_retry)) {
                            onEvent(MuscleVolumeEvent.Retry)
                        },
                        testTagPrefix = "muscle_sets_error",
                    )
                }

                is MuscleVolumeUiState.Content -> MuscleVolumeContent(state, onEvent)
            }
        }
    }
}

@Composable
private fun MuscleVolumeContent(state: MuscleVolumeUiState.Content, onEvent: (MuscleVolumeEvent) -> Unit) {
    val groups = state.history.series.keys.toList()
    val options = listOf(AppOption(stringResource(Res.string.muscle_volume_all), "muscle_sets_chip_all")) +
        groups.map { AppOption(stringResource(it.label), "muscle_sets_chip_${it.tagName()}") }
    val selectedIndex = state.selected?.let { groups.indexOf(it) + 1 }?.takeIf { it > 0 } ?: 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        FilterChipRow(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = { onEvent(MuscleVolumeEvent.SelectGroup(groups.getOrNull(it - 1))) },
        )
        Text(
            text = stringResource(Res.string.muscle_volume_caption),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textSecondary,
        )
        if (state.selected == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                groups.forEach { group ->
                    GroupOverviewCard(group, state.history, { onEvent(MuscleVolumeEvent.SelectGroup(group)) })
                }
            }
        } else {
            GroupChartCard(state.selected, state.history, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MuscleVolumeLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("muscle_sets_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(Spacing.minTouchTarget), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCardHeight), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCardHeight), AppShapes.lg)
    }
}
