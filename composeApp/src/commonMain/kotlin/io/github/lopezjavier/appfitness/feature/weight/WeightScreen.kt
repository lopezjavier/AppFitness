package io.github.lopezjavier.appfitness.feature.weight

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.state_retry
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_button_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_fat
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_label
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_when
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_when_day
import io.github.lopezjavier.appfitness.composeapp.resources.weight_edit_today_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_note
import io.github.lopezjavier.appfitness.composeapp.resources.weight_history_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_percent_value
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.EmptyState
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.MonthCalendarGrid
import io.github.lopezjavier.appfitness.designsystem.OfflineBanner
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClass
import io.github.lopezjavier.appfitness.designsystem.WindowWidthClassDefaults
import io.github.lopezjavier.appfitness.designsystem.windowWidthClassFor
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import io.github.lopezjavier.appfitness.feature.workout.shortLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private val SkeletonPanelHeight = Spacing.xl * 5
private val SkeletonButtonHeight = Spacing.minTouchTarget
private val SkeletonCalendarHeight = Spacing.xl * 11

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
    goal: GoalUiState? = null,
    onGoalEvent: (GoalEvent) -> Unit = {},
    snackbarHost: SnackbarHostState = remember { SnackbarHostState() },
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val goalContent = (goal as? GoalUiState.Content)?.content
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
                    goal,
                    onGoalEvent,
                )
                WeightSheets(state.content, goalContent, timeZone, onEvent, onGoalEvent)
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
@Suppress("LongParameterList", "LongMethod")
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
    goal: GoalUiState?,
    onGoalEvent: (GoalEvent) -> Unit,
) {
    val whrClick: (() -> Unit)? = (goal as? GoalUiState.Content)?.content?.waistToHip?.let {
        { onGoalEvent(GoalEvent.OpenMetric(MetricKind.WaistToHip)) }
    }
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
                            goal?.let { GoalSection(it, onGoalEvent) }
                            healthSync?.let { HealthConnectBanner(it, onHealthEvent) }
                            trend?.let { WeightChart(it, onTrendEvent) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                            WeightCalendar(content, onEvent)
                            CircumferenceSection(
                                circumferences,
                                content.today,
                                timeZone,
                                onCircumferenceEvent,
                                onRatioClick = whrClick,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.blockGap)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
                            WeightPanelSection(content, onEvent, timeZone, trend)
                            goal?.let { GoalSection(it, onGoalEvent) }
                            healthSync?.let { HealthConnectBanner(it, onHealthEvent) }
                            trend?.let { WeightChart(it, onTrendEvent) }
                        }
                        WeightCalendar(content, onEvent)
                        CircumferenceSection(
                            circumferences,
                            content.today,
                            timeZone,
                            onCircumferenceEvent,
                            onRatioClick = whrClick,
                        )
                    }
                }
            }
        }
    }
}

/** The day sheet the target sheet and the metric detail over the screen. */
@Composable
private fun WeightSheets(
    content: WeightContent,
    goal: GoalContent?,
    timeZone: TimeZone,
    onEvent: (WeightEvent) -> Unit,
    onGoalEvent: (GoalEvent) -> Unit,
) {
    content.sheet?.let { sheet ->
        WeightDaySheet(sheet, content.isOnline, timeZone, onEvent)
    }
    if (content.compositionOpen) {
        CompositionSheet(content.composition, timeZone, { onEvent(WeightEvent.CloseComposition) })
    }
    goal?.targetSheet?.let { TargetWeightSheet(it, content.isOnline, onGoalEvent) }
    val metric = goal?.openMetric?.let { kind ->
        when (kind) {
            MetricKind.Bmi -> goal.bmi
            MetricKind.BodyFat -> goal.bodyFat
            MetricKind.WaistToHip -> goal.waistToHip
        }
    }
    metric?.let { MetricDetailSheet(it, onGoalEvent) }
}

/** The current weight panel (opens the composition) with the pencil that edits today's weight; or the empty state. */
@Composable
private fun WeightPanelSection(
    content: WeightContent,
    onEvent: (WeightEvent) -> Unit,
    timeZone: TimeZone,
    trend: WeightTrendUiState?,
    modifier: Modifier = Modifier,
) {
    val current = content.current
    val editEnabled = content.days[content.today]?.hasHealthConnectWeight != true
    val onEditToday = { onEvent(WeightEvent.LogToday) }
    if (current == null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            EmptyState(
                message = stringResource(Res.string.weight_empty_message),
                modifier = Modifier.fillMaxWidth(),
                testTagPrefix = "weight_empty",
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                EditTodayButton(onEditToday, editEnabled)
            }
        }
    } else {
        WeightPanel(
            current,
            timeZone,
            trend,
            { onEvent(WeightEvent.OpenComposition) },
            onEditToday,
            editEnabled,
            modifier,
        )
    }
}

/** The pencil: opens today's weight sheet; inert (with a state description) when Health Connect owns today's weight. */
@Composable
private fun EditTodayButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val hcNote = stringResource(Res.string.weight_hc_note)
    AppIconButton(
        contentDescription = stringResource(Res.string.weight_edit_today_cd),
        icon = Res.drawable.ic_edit,
        testTag = "weight_edit_today_button",
        onClick = onClick,
        modifier = modifier.semantics { if (!enabled) stateDescription = hcNote },
        enabled = enabled,
        tint = AppTheme.colors.textSecondary,
    )
}

/** The latest measurement of any source: weight, when it was taken and the body fat when it has one. */
@Suppress("LongParameterList")
@Composable
private fun WeightPanel(
    current: BodyMeasurement,
    timeZone: TimeZone,
    trend: WeightTrendUiState?,
    onOpenComposition: () -> Unit,
    onEditToday: () -> Unit,
    editEnabled: Boolean,
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
    val openCd = stringResource(Res.string.weight_composition_button_cd)
    Box(
        modifier
            .fillMaxWidth()
            .clip(AppShapes.lg)
            .clickable(role = Role.Button, onClick = onOpenComposition)
            .semantics { contentDescription = openCd }
            .testTag("weight_panel"),
    ) {
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
        EditTodayButton(onEditToday, editEnabled, Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
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
