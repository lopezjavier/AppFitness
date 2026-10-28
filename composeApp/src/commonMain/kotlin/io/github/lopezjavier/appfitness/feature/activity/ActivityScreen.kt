package io.github.lopezjavier.appfitness.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.activity_active_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.activity_bpm_value
import io.github.lopezjavier.appfitness.composeapp.resources.activity_card_description
import io.github.lopezjavier.appfitness.composeapp.resources.activity_empty_action
import io.github.lopezjavier.appfitness.composeapp.resources.activity_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.activity_empty_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_error
import io.github.lopezjavier.appfitness.composeapp.resources.activity_kcal_value
import io.github.lopezjavier.appfitness.composeapp.resources.activity_resting_hr
import io.github.lopezjavier.appfitness.composeapp.resources.activity_sleep
import io.github.lopezjavier.appfitness.composeapp.resources.activity_steps
import io.github.lopezjavier.appfitness.composeapp.resources.activity_steps_value
import io.github.lopezjavier.appfitness.composeapp.resources.activity_title
import io.github.lopezjavier.appfitness.composeapp.resources.activity_total_kcal
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.OfflineState
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.feature.analytics.stats.durationText
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val STACK_FONT_SCALE = 1.5f
private const val SECONDS_PER_MINUTE = 60L
private const val SKELETON_CARDS = 4
private const val COLUMNS = 2

/** Stateful "Actividad" screen: connects [ActivityViewModel]. [onOpenWeight] leads the empty state to Peso. */
@Composable
fun ActivityRoot(
    onOpenWeight: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ActivityScreen(state, viewModel.isOnline, viewModel::onEvent, onOpenWeight, onBack, modifier)
}

/**
 * "Actividad" (US-61): one read-only [MetricCard] per stored value (sleep, steps, active and total calories, resting
 * heart rate), each dated; a value the watch does not provide has no card.
 */
@Composable
fun ActivityScreen(
    state: ActivityUiState,
    isOnline: Flow<Boolean>,
    onEvent: (ActivityEvent) -> Unit,
    onOpenWeight: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppScreen(
        title = stringResource(Res.string.activity_title),
        modifier = modifier.testTag("activity_screen"),
        onBack = onBack,
        maxContentWidth = WindowWidthClassDefaults.TwoColumnContentMaxWidth,
        testTagPrefix = "activity",
    ) {
        when (state) {
            ActivityUiState.Loading -> ActivityLoading()

            ActivityUiState.Empty -> CenteredState {
                EmptyState(
                    message = stringResource(Res.string.activity_empty_message),
                    title = stringResource(Res.string.activity_empty_title),
                    action = AppStateAction(stringResource(Res.string.activity_empty_action), onOpenWeight),
                    testTagPrefix = "activity_empty",
                )
            }

            ActivityUiState.Error -> CenteredState {
                ErrorState(
                    message = stringResource(Res.string.activity_error),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(ActivityEvent.Retry) },
                    testTagPrefix = "activity_error",
                    actionTestTag = "activity_retry",
                )
            }

            ActivityUiState.Offline -> CenteredState {
                OfflineState(isOnline = isOnline, onReload = { onEvent(ActivityEvent.Retry) })
            }

            is ActivityUiState.Content -> ActivityContent(state)
        }
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun ActivityContent(state: ActivityUiState.Content) {
    val sleep = state.sleep?.let { value ->
        ActivityCardData("activity_sleep_card", Res.string.activity_sleep, value) {
            durationText(it * SECONDS_PER_MINUTE)
        }
    }
    val cards = listOfNotNull(
        sleep,
        state.steps?.let { value ->
            ActivityCardData("activity_steps_card", Res.string.activity_steps, value) {
                stringResource(Res.string.activity_steps_value, AppNumberFormat.integer(it))
            }
        },
        state.activeKcal?.let { value ->
            ActivityCardData("activity_active_kcal_card", Res.string.activity_active_kcal, value) {
                stringResource(Res.string.activity_kcal_value, AppNumberFormat.integer(it))
            }
        },
        state.totalKcal?.let { value ->
            ActivityCardData("activity_total_kcal_card", Res.string.activity_total_kcal, value) {
                stringResource(Res.string.activity_kcal_value, AppNumberFormat.integer(it))
            }
        },
        state.restingHeartRate?.let { value ->
            ActivityCardData("activity_resting_hr_card", Res.string.activity_resting_hr, value) {
                stringResource(Res.string.activity_bpm_value, it)
            }
        },
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoColumns = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide &&
            LocalDensity.current.fontScale < STACK_FONT_SCALE
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (twoColumns) {
                cards.chunked(COLUMNS).forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        pair.forEach { ActivityCard(it, state.today, Modifier.weight(1f)) }
                        if (pair.size < COLUMNS) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                cards.forEach { ActivityCard(it, state.today) }
            }
        }
    }
}

private class ActivityCardData(
    val tag: String,
    val label: org.jetbrains.compose.resources.StringResource,
    val value: DayValue,
    val format: @Composable (Int) -> String,
)

@Composable
private fun ActivityCard(data: ActivityCardData, today: LocalDate, modifier: Modifier = Modifier) {
    val label = stringResource(data.label)
    val value = data.format(data.value.value)
    val day = AppDateFormat.list(data.value.day, today)
    val description = stringResource(Res.string.activity_card_description, label, value, day)
    MetricCard(
        label = label,
        value = value,
        modifier = modifier.testTag(data.tag).semantics(mergeDescendants = true) { contentDescription = description },
        supporting = {
            Text(
                text = day,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
            )
        },
    )
}

@Composable
private fun ActivityLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("activity_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(SKELETON_CARDS) {
            SkeletonBox(Modifier.fillMaxWidth().height(Spacing.xl * COLUMNS * COLUMNS), AppShapes.lg)
        }
    }
}
