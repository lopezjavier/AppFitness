package io.github.lopezjavier.appfitness.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.exercise_unnamed
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.strength_card_title
import io.github.lopezjavier.appfitness.composeapp.resources.strength_delta_down
import io.github.lopezjavier.appfitness.composeapp.resources.strength_delta_flat
import io.github.lopezjavier.appfitness.composeapp.resources.strength_delta_up
import io.github.lopezjavier.appfitness.composeapp.resources.strength_empty_cta
import io.github.lopezjavier.appfitness.composeapp.resources.strength_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.strength_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.strength_open_exercise
import io.github.lopezjavier.appfitness.composeapp.resources.strength_record
import io.github.lopezjavier.appfitness.composeapp.resources.strength_record_none
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.FilterChipRow
import io.github.lopezjavier.appfitness.designsystem.SecondaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrendDirection
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.domain.model.StrengthSummary
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val SkeletonRecordHeight = Spacing.xl * 2
private val SkeletonChipsHeight = Spacing.minTouchTarget

/**
 * Stateful "Fuerza" screen: connects [StrengthViewModel]. [onOpenExercise] opens the progress of an exercise and
 * [onStartWorkout] leads the empty state to the Entreno tab.
 */
@Composable
fun StrengthRoot(
    onOpenExercise: (String) -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StrengthViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StrengthScreen(state, viewModel::onEvent, onOpenExercise, onStartWorkout, modifier)
}

/**
 * "Fuerza y 1RM estimado" (US-15): the top exercises as chips, the 1RM record of the selected one, its change over the
 * last month and the way to its full progress.
 */
@Composable
fun StrengthScreen(
    state: StrengthUiState,
    onEvent: (StrengthEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().testTag("strength_screen"), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = WindowWidthClassDefaults.FormContentMaxWidth).fillMaxSize()) {
            when (state) {
                StrengthUiState.Loading -> StrengthLoading()

                StrengthUiState.Empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        message = stringResource(Res.string.strength_empty_message),
                        action = AppStateAction(stringResource(Res.string.strength_empty_cta), onStartWorkout),
                        testTagPrefix = "strength_empty",
                    )
                }

                StrengthUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        message = stringResource(Res.string.strength_error_message),
                        action = AppStateAction(stringResource(Res.string.state_retry)) {
                            onEvent(StrengthEvent.Retry)
                        },
                        testTagPrefix = "strength_error",
                    )
                }

                is StrengthUiState.Content -> StrengthContent(state, onEvent, onOpenExercise)
            }
        }
    }
}

@Composable
private fun StrengthContent(
    state: StrengthUiState.Content,
    onEvent: (StrengthEvent) -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val unnamed = stringResource(Res.string.exercise_unnamed)
    val options = state.chips.map { AppOption(it.name ?: unnamed, "strength_chip_${it.exerciseId}") }
    val selectedIndex = state.chips.indexOfFirst { it.exerciseId == state.selectedId }.takeIf { it >= 0 }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = stringResource(Res.string.strength_card_title),
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                FilterChipRow(
                    options = options,
                    selectedIndex = selectedIndex,
                    onSelect = { onEvent(StrengthEvent.SelectExercise(state.chips[it].exerciseId)) },
                )
                SummaryBlock(state.summary)
                SecondaryButton(
                    text = stringResource(Res.string.strength_open_exercise),
                    onClick = { onOpenExercise(state.selectedId) },
                    modifier = Modifier.fillMaxWidth().testTag("strength_open_exercise"),
                )
            }
        }
    }
}

/** The record and its monthly delta; a skeleton while the summary of a newly selected exercise loads. */
@Composable
private fun SummaryBlock(summary: StrengthSummary?) {
    if (summary == null) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRecordHeight).testTag("strength_summary_loading"))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        RecordText(summary.recordOneRepMax)
        summary.monthlyDelta?.let { DeltaPill(it) }
    }
}

@Composable
private fun RecordText(record: Double?) {
    if (record == null) {
        Text(
            text = stringResource(Res.string.strength_record_none),
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("strength_record"),
        )
        return
    }
    val full = stringResource(Res.string.strength_record, formatKilos(record))
    val number = formatKilos(record)
    val numberStart = full.indexOf(number)
    val value = AppTheme.typography.headlineMetric
    val text = buildAnnotatedString {
        append(full.substring(0, numberStart))
        withStyle(
            SpanStyle(
                color = AppTheme.colors.athleticCyan,
                fontSize = value.fontSize,
                fontWeight = value.fontWeight,
                fontFeatureSettings = value.fontFeatureSettings,
            ),
        ) { append(number) }
        append(full.substring(numberStart + number.length))
    }
    Text(
        text = text,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colors.textSecondary,
        modifier = Modifier.testTag("strength_record"),
    )
}

/** Favorable only when the record grew; a flat or lower month is neutral (a deload is normal), never red. */
@Composable
private fun DeltaPill(delta: Double) {
    val direction = TrendPillDefaults.direction(delta)
    val rounded = formatSignedKilos(delta)
    val (text, tone) = when (direction) {
        TrendDirection.Up -> stringResource(Res.string.strength_delta_up, rounded) to TrendTone.Favorable
        TrendDirection.Down -> stringResource(Res.string.strength_delta_down, rounded) to TrendTone.Neutral
        else -> stringResource(Res.string.strength_delta_flat) to TrendTone.Neutral
    }
    TrendPill(text, tone, Modifier.testTag("strength_delta"), direction)
}

@Composable
private fun StrengthLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("strength_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonChipsHeight), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonRecordHeight), AppShapes.lg)
    }
}
