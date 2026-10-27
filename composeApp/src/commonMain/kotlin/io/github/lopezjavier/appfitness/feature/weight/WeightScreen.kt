package io.github.lopezjavier.appfitness.feature.weight

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.ic_calendar
import io.github.lopezjavier.appfitness.composeapp.resources.ic_edit
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_absent
import io.github.lopezjavier.appfitness.composeapp.resources.weight_composition_button_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_label
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_value
import io.github.lopezjavier.appfitness.composeapp.resources.weight_current_when
import io.github.lopezjavier.appfitness.composeapp.resources.weight_edit_today_cd
import io.github.lopezjavier.appfitness.composeapp.resources.weight_empty_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_error_message
import io.github.lopezjavier.appfitness.composeapp.resources.weight_hc_note
import io.github.lopezjavier.appfitness.composeapp.resources.weight_history_title
import io.github.lopezjavier.appfitness.composeapp.resources.weight_title
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppIconButton
import io.github.lopezjavier.appfitness.designsystem.AppScreen
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.AppTopBarAction
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.SkeletonBox
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.BodyMeasurement
import io.github.lopezjavier.appfitness.domain.model.MeasurementSource
import io.github.lopezjavier.appfitness.domain.usecase.MetricKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

private val SkeletonPanelHeight = Spacing.xl * 5

/**
 * Stateless weight screen (US-25) on an `AppScreen`: "Peso actual" (opens the composition; the pencil edits today),
 * "Objetivo", the BMI and body-fat cards, the trend chart and the circumferences. The calendar action of the top bar
 * opens the measurement history ([onOpenHistory]). The day sheet, target sheet and metric detail open over it.
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
    onOpenHistory: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    val goalContent = (goal as? GoalUiState.Content)?.content
    val historyTitle = stringResource(Res.string.weight_history_title)
    val actions = remember(historyTitle, onOpenHistory) {
        listOf(AppTopBarAction(Res.drawable.ic_calendar, historyTitle, onOpenHistory))
    }
    AppScreen(
        title = stringResource(Res.string.weight_title),
        modifier = modifier,
        onBack = onBack,
        actions = actions,
        testTagPrefix = "weight",
    ) {
        Box(Modifier.fillMaxSize().testTag("weight_screen")) {
            when (state) {
                WeightUiState.Loading -> WeightLoading()

                WeightUiState.Error -> ErrorState(
                    message = stringResource(Res.string.weight_error_message),
                    action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(WeightEvent.Retry) },
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
    goal: GoalUiState?,
    onGoalEvent: (GoalEvent) -> Unit,
) {
    val whrClick: (() -> Unit)? = (goal as? GoalUiState.Content)?.content?.waistToHip?.let {
        { onGoalEvent(GoalEvent.OpenMetric(MetricKind.WaistToHip)) }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenMargin),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            CurrentWeightCard(content, onEvent, timeZone, trend, healthSync, onHealthEvent)
            goal?.let {
                GoalCardSection(it, onGoalEvent)
                MetricCardsSection(it, onGoalEvent)
            }
            trend?.let { WeightChart(it, onTrendEvent) }
        }
        CircumferenceSection(
            circumferences,
            content.today,
            timeZone,
            onCircumferenceEvent,
            onRatioClick = whrClick,
        )
    }
}

/** The day sheet, the composition, the target sheet and the metric detail over the screen. */
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

/**
 * "Peso actual": the latest weight of any source with when it was taken, the weekly rate and the Health Connect
 * status with its button. The label and value open the composition; the pencil edits today's weight.
 */
// Screen state plus its event sinks.
@Suppress("LongParameterList")
@Composable
private fun CurrentWeightCard(
    content: WeightContent,
    onEvent: (WeightEvent) -> Unit,
    timeZone: TimeZone,
    trend: WeightTrendUiState?,
    healthSync: HealthSyncUiState?,
    onHealthEvent: (HealthSyncEvent) -> Unit,
) {
    val current = content.current
    val editEnabled = content.days[content.today]?.hasHealthConnectWeight != true
    MetricCard(
        label = stringResource(Res.string.weight_current_label),
        value = current?.let { stringResource(Res.string.weight_current_value, decimalText(it.weightKg)) }
            ?: stringResource(Res.string.weight_composition_absent),
        modifier = Modifier.testTag("weight_current_card"),
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (current == null) {
                    Text(
                        text = stringResource(Res.string.weight_empty_message),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("weight_empty"),
                    )
                } else {
                    Text(
                        text = whenText(current, content.today, timeZone),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("weight_current_when"),
                    )
                    trend?.let { WeeklyRatePill(it) }
                }
                healthSync?.let { HealthStatusLine(it, onHealthEvent) }
            }
        },
        trailingAction = { EditTodayButton({ onEvent(WeightEvent.LogToday) }, editEnabled) },
        onClick = if (current != null) {
            { onEvent(WeightEvent.OpenComposition) }
        } else {
            null
        },
        clickLabel = stringResource(Res.string.weight_composition_button_cd),
    )
}

/** "Hoy", "Ayer" or "lun, 24 oct"; an imported reading adds its time. */
@Composable
private fun whenText(current: BodyMeasurement, today: LocalDate, timeZone: TimeZone): String {
    val local = current.measuredAt.toLocalDateTime(timeZone)
    val day = AppDateFormat.list(local.date, today)
    // Only the day matters for a manual weight; imported readings keep their time.
    return if (current.source == MeasurementSource.Manual) {
        day
    } else {
        stringResource(Res.string.weight_current_when, day, timeText(local.hour, local.minute))
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

@Composable
private fun WeightLoading() {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenMargin).testTag("weight_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.blockGap),
    ) {
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonPanelHeight), AppShapes.lg)
        SkeletonBox(Modifier.fillMaxWidth().height(SkeletonPanelHeight), AppShapes.lg)
    }
}

internal fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

internal fun timeText(hour: Int, minute: Int): String = "${twoDigits(hour)}:${twoDigits(minute)}"
