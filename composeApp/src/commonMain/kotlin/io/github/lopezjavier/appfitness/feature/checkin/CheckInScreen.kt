package io.github.lopezjavier.appfitness.feature.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.action_retry
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_delta_caption
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_delta_same_short
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_energy_group_cd
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_energy_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_energy_option_cd
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_form_header
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_kg_unit
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_records_empty
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_records_header
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_sets_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_summary_caption
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_summary_error
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_summary_header
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_title
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_volume_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_delta_caption
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_not_enough
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_weight_value
import io.github.lopezjavier.appfitness.composeapp.resources.checkin_workouts_label
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_bullet_1
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_bullet_2
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_bullet_3
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_bullet_4
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_bullet_5
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_cd
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_intro
import io.github.lopezjavier.appfitness.composeapp.resources.help_checkin_energy_title
import io.github.lopezjavier.appfitness.composeapp.resources.ic_bar_chart
import io.github.lopezjavier.appfitness.composeapp.resources.ic_fitness_center
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.ic_monitor_weight
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_checkin_energy_high
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_checkin_energy_low
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_checkin_record_badge
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_checkin_weight_down
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_checkin_weight_flat
import io.github.lopezjavier.appfitness.composeapp.resources.px_diary_checkin_weight_up
import io.github.lopezjavier.appfitness.designsystem.AppCard
import io.github.lopezjavier.appfitness.designsystem.AppEditorScreen
import io.github.lopezjavier.appfitness.designsystem.AppListRow
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppOption
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppStateAction
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.DiaryEnergyScale
import io.github.lopezjavier.appfitness.designsystem.ErrorState
import io.github.lopezjavier.appfitness.designsystem.HelpButton
import io.github.lopezjavier.appfitness.designsystem.HelpContent
import io.github.lopezjavier.appfitness.designsystem.IconBadge
import io.github.lopezjavier.appfitness.designsystem.IconBadgeDefaults
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.MetricCard
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.MetricTileRow
import io.github.lopezjavier.appfitness.designsystem.RecordBadge
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.TrendPill
import io.github.lopezjavier.appfitness.designsystem.TrendPillDefaults
import io.github.lopezjavier.appfitness.designsystem.TrendTone
import io.github.lopezjavier.appfitness.domain.model.PersonalRecord
import io.github.lopezjavier.appfitness.domain.model.WeekRecord
import io.github.lopezjavier.appfitness.domain.model.WeeklySummary
import io.github.lopezjavier.appfitness.domain.model.WeeklyWeightTrend
import io.github.lopezjavier.appfitness.feature.exercises.valueText
import io.github.lopezjavier.appfitness.feature.workout.label
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.DayOfWeek
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val TENTHS = 10.0

/**
 * The weekly check-in (US-59) on [AppEditorScreen]: the closed week in figures, a slot for the coach (E13, empty) and
 * the energy form above a pinned "Guardar". The summary has its own loading and error states and never blocks
 * the form. The template owns the discard dialog, the offline hint and the Confirm haptic when the editor closes after
 * [CheckInEffect.Saved]; a failed save is a Reject haptic plus a message (R-28, R-37).
 */
@Composable
fun CheckInScreen(
    state: CheckInUiState,
    onEvent: (CheckInEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    effects: Flow<CheckInEffect> = emptyFlow(),
) {
    var saved by rememberSaveable { mutableStateOf(false) }
    CheckInEffects(effects) { saved = true }
    val title = stringResource(Res.string.checkin_title)
    if (state !is CheckInUiState.Ready) {
        CheckInPlaceholder(state, onEvent, onClose, title, modifier)
        return
    }
    AppEditorScreen(
        title = title,
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        isOnline = !state.isOffline,
        onSave = { onEvent(CheckInEvent.Save) },
        onClose = onClose,
        modifier = modifier,
        saved = saved,
        canSave = state.energy != null,
        testTagPrefix = "checkin",
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.lg)
                .testTag("checkin_screen"),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            SummaryBlock(state, onEvent)
            // Reserved for the coach's weekly read (E13, US-71): no UI yet.
            Box(Modifier.testTag("checkin_coach_slot"))
            FormBlock(state, onEvent)
        }
    }
}

@Composable
private fun SummaryBlock(state: CheckInUiState.Ready, onEvent: (CheckInEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ListSectionHeader(stringResource(Res.string.checkin_summary_header), Modifier.testTag("checkin_summary_header"))
        if (state.window.day.dayOfWeek != DayOfWeek.SUNDAY) {
            Text(
                text = stringResource(Res.string.checkin_summary_caption),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
        when (val summary = state.summary) {
            CheckInSummaryState.Loading -> Column(
                Modifier.testTag("checkin_summary_loading"),
                verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            ) { SummarySkeleton() }

            CheckInSummaryState.Failed -> ErrorState(
                message = stringResource(Res.string.checkin_summary_error),
                action = AppStateAction(stringResource(Res.string.action_retry)) { onEvent(CheckInEvent.RetrySummary) },
                modifier = Modifier.fillMaxWidth(),
                actionTestTag = "checkin_summary_retry",
                testTagPrefix = "checkin_summary_error",
            )

            is CheckInSummaryState.Loaded -> SummaryCards(summary.summary, state.exerciseNames)
        }
    }
}

@Composable
private fun SummaryCards(summary: WeeklySummary, names: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // C-24: three figures side by side, each with its change as a pill; one caption says what it compares to.
            MetricTileRow(weekFigures(summary).map { figure -> { m -> WeekTile(figure, m) } })
            Text(
                text = stringResource(Res.string.checkin_delta_caption),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.testTag("checkin_summary_delta_caption"),
            )
        }
        RecordsCard(summary.records, names)
        WeightCard(summary.weightTrend)
    }
}

@Composable
private fun weekFigures(summary: WeeklySummary): List<WeekFigure> {
    val current = summary.training.current
    val previous = summary.training.previous
    val volume = current.volume.roundToInt()
    return listOf(
        WeekFigure(
            label = stringResource(Res.string.checkin_volume_label),
            value = AppNumberFormat.integer(volume),
            unit = stringResource(Res.string.checkin_kg_unit),
            delta = volume - previous.volume.roundToInt(),
            tag = "checkin_summary_volume",
            icon = Res.drawable.ic_fitness_center,
            accent = AppTheme.colors.athleticCyan,
        ),
        WeekFigure(
            label = stringResource(Res.string.checkin_workouts_label),
            value = AppNumberFormat.integer(current.workouts),
            unit = null,
            delta = current.workouts - previous.workouts,
            tag = "checkin_summary_workouts",
            icon = Res.drawable.ic_flame_outlined,
            accent = AppTheme.colors.neonLime,
        ),
        WeekFigure(
            label = stringResource(Res.string.checkin_sets_label),
            value = AppNumberFormat.integer(current.sets),
            unit = null,
            delta = current.sets - previous.sets,
            tag = "checkin_summary_sets",
            icon = Res.drawable.ic_bar_chart,
            accent = AppTheme.colors.macroFat,
        ),
    )
}

/** The content of one [WeekTile]. */
@Suppress("LongParameterList") // A plain holder of one tile.
private class WeekFigure(
    val label: String,
    val value: String,
    val unit: String?,
    val delta: Int,
    val tag: String,
    val icon: DrawableResource,
    val accent: Color,
)

/** One figure of the week with its signed difference to the week before as a pill (sign and words, not colour). */
@Composable
private fun WeekTile(figure: WeekFigure, modifier: Modifier = Modifier) {
    val deltaText = if (figure.delta == 0) {
        stringResource(Res.string.checkin_delta_same_short)
    } else {
        AppNumberFormat.signedInteger(figure.delta)
    }
    MetricTile(
        label = figure.label,
        value = figure.value,
        modifier = modifier.testTag(figure.tag),
        unit = figure.unit,
        accent = figure.accent,
        icon = figure.icon,
        supporting = {
            TrendPill(deltaText, TrendTone.Neutral, direction = TrendPillDefaults.direction(figure.delta.toDouble()))
        },
    )
}

@Composable
private fun WeightCard(trend: WeeklyWeightTrend) {
    val label = stringResource(Res.string.checkin_weight_label)
    when (trend) {
        is WeeklyWeightTrend.Delta -> {
            val tenths = (trend.kgDelta * TENTHS).roundToInt() / TENTHS
            val signed = (if (tenths > 0) "+" else "") + AppNumberFormat.decimal(tenths)
            val value = stringResource(Res.string.checkin_weight_value, signed)
            val caption = stringResource(Res.string.checkin_weight_delta_caption)
            val words = stringResource(
                when {
                    tenths < 0 -> Res.string.px_diary_checkin_weight_down
                    tenths > 0 -> Res.string.px_diary_checkin_weight_up
                    else -> Res.string.px_diary_checkin_weight_flat
                },
            )
            AppCard(
                Modifier.fillMaxWidth().testTag("checkin_summary_weight").semantics(mergeDescendants = true) {
                    contentDescription = "$label $value, $caption"
                },
                glow = AppTheme.colors.athleticCyan,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    IconBadge(
                        icon = Res.drawable.ic_monitor_weight,
                        tint = AppTheme.colors.athleticCyan,
                        size = IconBadgeDefaults.LargeSize,
                        iconSize = IconBadgeDefaults.LargeIconSize,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(label, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textSecondary)
                        Text(value, style = AppTheme.typography.headlineMetric, color = AppTheme.colors.textPrimary)
                        Text(caption, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
                    }
                    TrendPill(words, TrendTone.Neutral, direction = TrendPillDefaults.direction(tenths))
                }
            }
        }

        WeeklyWeightTrend.NotEnoughData -> MetricCard(
            label = label,
            value = stringResource(Res.string.checkin_weight_not_enough),
            modifier = Modifier.testTag("checkin_summary_weight").semantics(mergeDescendants = true) {},
        )
    }
}

@Composable
private fun RecordsCard(records: List<WeekRecord>, names: Map<String, String>) {
    AppCard(Modifier.fillMaxWidth().testTag("checkin_summary_records")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ListSectionHeader(stringResource(Res.string.checkin_records_header))
            if (records.isEmpty()) {
                Text(
                    text = stringResource(Res.string.checkin_records_empty),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.testTag("checkin_summary_records_empty"),
                )
            }
            records.forEachIndexed { index, record ->
                val value = PersonalRecord(record.recordType, record.value, null, record.sessionDate).valueText()
                AppListRow(
                    headline = names.getValue(record.exerciseId),
                    supporting = "${record.recordType.label()} · $value",
                    modifier = Modifier.testTag("checkin_summary_record_$index").semantics(mergeDescendants = true) {},
                    leading = { IconBadge(Res.drawable.ic_fitness_center, AppPaint.Gold) },
                    trailing = { RecordBadge(stringResource(Res.string.px_diary_checkin_record_badge)) },
                )
            }
        }
    }
}

/** "?" beside the Energía label: what each value 1 to 5 means (R-45). */
@Composable
private fun CheckInEnergyHelpButton() {
    HelpButton(
        contentDescription = stringResource(Res.string.help_checkin_energy_cd),
        title = stringResource(Res.string.help_checkin_energy_title),
        content = HelpContent(
            paragraphs = listOf(stringResource(Res.string.help_checkin_energy_intro)),
            bullets = listOf(
                stringResource(Res.string.help_checkin_energy_bullet_1),
                stringResource(Res.string.help_checkin_energy_bullet_2),
                stringResource(Res.string.help_checkin_energy_bullet_3),
                stringResource(Res.string.help_checkin_energy_bullet_4),
                stringResource(Res.string.help_checkin_energy_bullet_5),
            ),
        ),
        testTag = "checkin_energy_help",
    )
}

@Composable
private fun FormBlock(state: CheckInUiState.Ready, onEvent: (CheckInEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        ListSectionHeader(stringResource(Res.string.checkin_form_header))
        val groupCd = stringResource(Res.string.checkin_energy_group_cd)
        Column(
            Modifier.testTag("checkin_energy").semantics { contentDescription = groupCd },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.checkin_energy_label),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                CheckInEnergyHelpButton()
            }
            val options = CHECK_IN_ENERGY_RANGE.map { level ->
                AppOption(
                    label = level.toString(),
                    testTag = "checkin_energy_$level",
                    contentDescription = stringResource(Res.string.checkin_energy_option_cd, level),
                )
            }
            DiaryEnergyScale(
                options = options,
                selectedIndex = state.energy?.let { CHECK_IN_ENERGY_RANGE.indexOf(it) },
                onSelect = { onEvent(CheckInEvent.SelectEnergy(CHECK_IN_ENERGY_RANGE.elementAt(it))) },
                lowLabel = stringResource(Res.string.px_diary_checkin_energy_low),
                highLabel = stringResource(Res.string.px_diary_checkin_energy_high),
                enabled = !state.isSaving,
            )
        }
    }
}
