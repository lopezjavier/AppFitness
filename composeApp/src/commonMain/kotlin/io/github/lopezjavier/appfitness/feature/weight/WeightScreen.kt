package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.weekly_rate_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_label
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_when
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_when_day
import io.github.lopezjavier.appfitness.composeapp.resources.weight_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_history_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_log_today
import io.github.lopezjavier.appfitness.composeapp.resources.weight_percent_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_trend_pill
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.PrimaryButton
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private val SkeletonPanelHeight = Spacing.xl * 5
private val SkeletonButtonHeight = Spacing.minTouchTarget
private val SkeletonCalendarHeight = Spacing.xl * 11
private val SkeletonPillWidth = Spacing.xl * 6
private val SkeletonPillHeight = Spacing.xl + Spacing.sm

/**
 * Stateless weight screen (US-25): the current weight with the "Registrar peso de hoy" button, and the month calendar
 * of the measurements; a day sheet opens over it. Wide windows put the panel and the calendar side by side. The
 * sections are separate composables so later tasks (chart, Health Connect) can extend this destination.
 */
@Composable
fun WeightScreen(
    state: WeightUiState,
    onEvent: (WeightEvent) -> Unit,
    circumferences: CircumferencesUiState,
    onCircumferenceEvent: (CircumferencesEvent) -> Unit,
    modifier: Modifier = Modifier,
    healthSync: HealthSyncUiState? = null,
    onHealthEvent: (HealthSyncEvent) -> Unit = {},
    trend: WeightTrendUiState? = null,
    onTrendEvent: (WeightTrendEvent) -> Unit = {},
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    Box(modifier.fillMaxSize().testTag("weight_screen")) {
        when (state) {
            WeightUiState.Loading -> WeightLoading()

            WeightUiState.Error -> ErrorState(
                message = stringResource(Res.string.weight_error_message),
                action = AppStateAction(stringResource(Res.string.state_retry)) { onEvent(WeightEvent.Retry) },
                modifier = Modifier.align(Alignment.Center),
                testTagPrefix = "weight_error",
            )

            is WeightUiState.Content -> {
                WeightContentView(
                    state.content,
                    onEvent,
                    circumferences,
                    onCircumferenceEvent,
                    healthSync,
                    onHealthEvent,
                    trend,
                    onTrendEvent,
                    timeZone,
                )
                state.content.sheet?.let { WeightDaySheet(it, state.content.isOnline, timeZone, onEvent) }
                val circumferenceSheet = (circumferences as? CircumferencesUiState.Content)?.content
                circumferenceSheet?.sheet?.let {
                    CircumferenceSheet(it, circumferenceSheet.isOnline, state.content.today, onCircumferenceEvent)
                }
            }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter)) { data ->
            Snackbar { Text(data.visuals.message) }
        }
    }
}

// Screen state plus its event sinks; the sections are separate composables.
@Suppress("LongParameterList")
@Composable
private fun WeightContentView(
    content: WeightContent,
    onEvent: (WeightEvent) -> Unit,
    circumferences: CircumferencesUiState,
    onCircumferenceEvent: (CircumferencesEvent) -> Unit,
    healthSync: HealthSyncUiState?,
    onHealthEvent: (HealthSyncEvent) -> Unit,
    trend: WeightTrendUiState?,
    onTrendEvent: (WeightTrendEvent) -> Unit,
    timeZone: TimeZone,
) {
    Column(Modifier.fillMaxSize()) {
        if (!content.isOnline) {
            OfflineBanner(Modifier.fillMaxWidth(), testTagPrefix = "weight_offline_banner")
        }
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val wide = windowWidthClassFor(maxWidth) == WindowWidthClass.Wide
            val maxContentWidth = if (wide) {
                WindowWidthClassDefaults.TwoColumnContentMaxWidth
            } else {
                WindowWidthClassDefaults.CardColumnMaxWidth
            }
            Column(
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.screenMargin),
            ) {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                            WeightPanelSection(content, onEvent, timeZone, trend)
                            healthSync?.let { HealthConnectBanner(it, onHealthEvent) }
                            trend?.let { WeightChart(it, onTrendEvent) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                            WeightCalendar(content, onEvent)
                            CircumferenceSection(circumferences, content.today, timeZone, onCircumferenceEvent)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                            WeightPanelSection(content, onEvent, timeZone, trend)
                            healthSync?.let { HealthConnectBanner(it, onHealthEvent) }
                            trend?.let { WeightChart(it, onTrendEvent) }
                        }
                        WeightCalendar(content, onEvent)
                        CircumferenceSection(circumferences, content.today, timeZone, onCircumferenceEvent)
                    }
                }
            }
        }
    }
}

/** The current weight (or the empty state) and the full-width "Registrar peso de hoy" button under it. */
@Composable
private fun WeightPanelSection(
    content: WeightContent,
    onEvent: (WeightEvent) -> Unit,
    timeZone: TimeZone,
    trend: WeightTrendUiState?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
        val current = content.current
        if (current == null) {
            EmptyState(
                message = stringResource(Res.string.weight_empty_message),
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "weight_empty",
            )
        } else {
            WeightPanel(current, timeZone, trend)
        }
        PrimaryButton(
            text = stringResource(Res.string.weight_log_today),
            onClick = { onEvent(WeightEvent.LogToday) },
            modifier = Modifier.fillMaxWidth().testTag("weight_log_today_button"),
        )
    }
}

/** The latest measurement of any source: weight, when it was taken and the body fat when it has one. */
@Composable
private fun WeightPanel(
    current: BodyMeasurement,
    timeZone: TimeZone,
    trend: WeightTrendUiState?,
    modifier: Modifier = Modifier,
) {
    val local = current.measuredAt.toLocalDateTime(timeZone)
    val dayText = "${local.day} ${local.month.shortLabel()}"
    // Only the day matters for a manual weight; imported readings keep their time.
    val whenText = if (current.source == MeasurementSource.Manual) {
        stringResource(Res.string.weight_current_when_day, dayText)
    } else {
        stringResource(Res.string.weight_current_when, dayText, timeText(local.hour, local.minute))
    }
    val fatText = current.bodyFatPct?.let {
        stringResource(Res.string.weight_current_fat, stringResource(Res.string.weight_percent_value, decimalText(it)))
    }
    Column(modifier.fillMaxWidth().testTag("weight_panel")) {
        MetricTile(
            label = stringResource(Res.string.weight_current_label),
            value = stringResource(Res.string.weight_current_value, decimalText(current.weightKg)),
            modifier = Modifier.fillMaxWidth().testTag("weight_current_value"),
            supporting = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = whenText,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("weight_current_when"),
                    )
                    fatText?.let {
                        Text(
                            text = it,
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                            modifier = Modifier.testTag("weight_current_fat"),
                        )
                    }
                    trend?.let { WeeklyRatePill(it) }
                }
            },
        )
    }
}

/** "Historial de mediciones": the month calendar; every past day can be opened. */
@Composable
private fun WeightCalendar(content: WeightContent, onEvent: (WeightEvent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.testTag("weight_calendar"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = stringResource(Res.string.weight_history_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("weight_history_title"),
        )
        MonthCalendarGrid(
            month = content.month,
            markedDays = content.markedDays,
            selected = content.sheet?.day,
            today = content.today,
            onSelect = { onEvent(WeightEvent.OpenDay(it)) },
            onPrevMonth = { onEvent(WeightEvent.PreviousMonth) },
            onNextMonth = { onEvent(WeightEvent.NextMonth) },
            canGoNext = content.canGoNext,
        )
    }
}

@Composable
private fun WeightLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("weight_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonPanelHeight), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonButtonHeight), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonCalendarHeight), AppShapes.lg)
    }
}

internal fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

internal fun timeText(hour: Int, minute: Int): String = "${twoDigits(hour)}:${twoDigits(minute)}"

/** The weekly rate under the current weight: signed value coloured by the goal, or "Sin datos suficientes". */
@Composable
private fun WeeklyRatePill(trend: WeightTrendUiState) {
    when (trend) {
        WeightTrendUiState.Loading ->
            SkeletonBox(
                Modifier.size(SkeletonPillWidth, SkeletonPillHeight).testTag("weight_trend_loading"),
                AppShapes.full,
            )

        WeightTrendUiState.Error -> Unit

        is WeightTrendUiState.Content -> {
            val rate = trend.trend.rate
            val value = rate.value
            TrendPill(
                text = if (value == null) {
                    stringResource(Res.string.weekly_rate_no_data)
                } else {
                    stringResource(Res.string.weight_trend_pill, rateText(value))
                },
                tone = rate.tone.toTrendTone(),
                modifier = Modifier.testTag("weight_trend_pill"),
                direction = TrendPillDefaults.direction(value),
                containerColor = AppTheme.colors.surfaceRaised,
            )
        }
    }
}
