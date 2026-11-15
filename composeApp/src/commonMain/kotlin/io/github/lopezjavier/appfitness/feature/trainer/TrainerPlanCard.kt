package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_eyebrow
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_goal
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_today
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_today_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_goal_meta
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pace_actual
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pace_actual_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pace_goal
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_rate_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tile_calories
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tile_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tile_of_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tiles_caption
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tiles_caption_empty
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_cut_faster
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_cut_not_falling
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_cut_slower
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_early_cut
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_falling
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_gain_faster
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_gain_not_rising
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_gain_slower
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_no_data
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_on_pace
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_rising
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_verdict_stable
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppLinearProgress
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.NonBreaking
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatusChip
import io.github.lopezjavier.appfitness.designsystem.StatusTone
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.designsystem.WeightPaceBandPoint
import io.github.lopezjavier.appfitness.designsystem.WeightPaceChart
import io.github.lopezjavier.appfitness.designsystem.WeightPaceChartData
import io.github.lopezjavier.appfitness.designsystem.WeightPaceLabel
import io.github.lopezjavier.appfitness.designsystem.WeightPacePlanLine
import io.github.lopezjavier.appfitness.designsystem.WeightPacePoint
import io.github.lopezjavier.appfitness.domain.model.CoachPlan
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.usecase.WeightPace
import io.github.lopezjavier.appfitness.domain.usecase.WeightPaceState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CENTS = 100
private const val RATE_EPSILON = 0.005
private const val WEEK_CENTRE = 0.5f
private const val MIN_LABEL_GAP_WEEKS = 1.5f
private const val PERCENT = 100.0
private const val MINUS = '−'
private const val NO_VALUE = "–"
private const val TILE_STAGGER = 1
private const val KEY_DASH_FACTOR = 1.5f
private val TileMinHeight = Spacing.minTouchTarget + Spacing.xl + Spacing.md + Spacing.xs

/** A rate with its sign and two decimals ("+0,18", "−0,05"); zero has no sign. True minus sign (U+2212). */
internal fun signedHundredths(value: Double): String {
    val cents = (abs(value) * CENTS).roundToInt()
    val sign = when {
        cents == 0 -> ""
        value < 0 -> MINUS.toString()
        else -> "+"
    }
    return "$sign${cents / CENTS},${(cents % CENTS).toString().padStart(2, '0')}"
}

/**
 * The accepted plan's card on the Trainer root (C-131..C-138): the goal header and the weight pace chart (the tappable
 * body that opens "Tu plan"), then, outside that tap target so the "?" and the button keep their own targets (R-103),
 * the figures line, the verdict, the weeks in the phase and the Calorías tile with its scope line. A summary only: "Crear
 * plan nuevo" lives in "Tu plan".
 */
@Suppress("LongParameterList") // The plan, the figures around it and the card's two events.
@Composable
internal fun PlanCard(
    plan: CoachPlan,
    pace: WeightPace?,
    weeksInPhase: PhaseWeeks?,
    diaryCalories: DiaryCalories?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SummaryCard(
        title = stringResource(Res.string.trainer_plan_section),
        lines = emptyList(),
        onClick = onOpen,
        testTag = "trainer_box_plan",
        modifier = modifier,
        content = { PlanChartPanel(plan, pace) },
        footer = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PaceFigures(plan, pace)
                if (pace != null) PaceVerdict(pace)
                weeksInPhase?.takeIf { it.weeks > 0 }?.let { WeeksInPhaseLine(it) }
                PlanTiles(plan, diaryCalories)
            }
        },
    )
}

/** The panel: eyebrow, goal name, planned rate chip and the "Meta" line over the chart (C-131). */
@Composable
private fun PlanChartPanel(plan: CoachPlan, pace: WeightPace?) {
    val rate = plan.nutrition.rateKgWeek
    val outcome = plan.weightOutcome
    Column(
        Modifier
            .padding(top = Spacing.xs)
            .fillMaxWidth()
            .background(AppTheme.colors.surfaceControl, AppShapes.md)
            .padding(Spacing.md)
            .testTag("trainer_box_plan_goal"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.trainer_goal_eyebrow).uppercase(),
                    style = AppTheme.typography.labelTechnical,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    text = stringResource(PhaseText.label(plan.phase)),
                    style = AppTheme.typography.titleSection,
                    color = AppTheme.colors.textPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (abs(rate) >= RATE_EPSILON) {
                    StatusChip(
                        text = NonBreaking.join(signedHundredths(rate), stringResource(Res.string.trainer_plan_rate_unit)),
                        tone = StatusTone.Info,
                        modifier = Modifier.testTag("trainer_box_plan_rate"),
                    )
                }
                outcome?.let {
                    Text(
                        text = stringResource(
                            Res.string.trainer_plan_goal_meta,
                            AppNumberFormat.decimal(it.targetValue),
                            AppDateFormat.shortDate(it.deadline),
                        ),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.testTag("trainer_box_plan_meta"),
                    )
                }
            }
        }
        if (pace != null && (pace.planLine != null || pace.weekly.isNotEmpty())) {
            WeightPaceChart(
                data = chartDataOf(pace),
                valueLabel = { AppNumberFormat.decimal(it) },
                modifier = Modifier.testTag("trainer_box_plan_chart"),
            )
        }
    }
}

/** Maps the fit to the chart's plain data: weekly points sit mid-week (index + 0.5), the band spans the trend only. */
@Composable
private fun chartDataOf(pace: WeightPace): WeightPaceChartData {
    val weekly = pace.weekly.map { WeightPacePoint(it.index + WEEK_CENTRE, it.kg, it.partial) }
    val plan = pace.planLine?.let {
        WeightPacePlanLine(it.startWeeks.toFloat(), it.startKg, it.endWeeks.toFloat(), it.endKg)
    }
    val latest = weekly.lastOrNull()
    val todayX = latest?.x ?: (pace.windowWeeks - WEEK_CENTRE)
    val labels = buildList {
        add(WeightPaceLabel(WEEK_CENTRE, stringResource(Res.string.trainer_plan_chart_week, 1)))
        val middle = (pace.windowWeeks / 2) + WEEK_CENTRE
        if (pace.windowWeeks >= MIDDLE_LABEL_MIN_WEEKS && middle - WEEK_CENTRE >= MIN_LABEL_GAP_WEEKS &&
            todayX - middle >= MIN_LABEL_GAP_WEEKS
        ) {
            add(WeightPaceLabel(middle, stringResource(Res.string.trainer_plan_chart_week, pace.windowWeeks / 2 + 1)))
        }
        add(
            WeightPaceLabel(
                todayX,
                latest?.let {
                    stringResource(Res.string.trainer_plan_chart_today_value, AppNumberFormat.decimal(it.kg))
                } ?: stringResource(Res.string.trainer_plan_chart_today),
                emphasized = true,
            ),
        )
        if (plan != null && plan.endX - todayX >= MIN_LABEL_GAP_WEEKS) {
            add(WeightPaceLabel(plan.endX, stringResource(Res.string.trainer_plan_chart_goal)))
        }
    }
    return WeightPaceChartData(
        weekly = weekly,
        daily = pace.daily.map { WeightPacePoint(it.weeks.toFloat(), it.kg) },
        plan = plan,
        band = bandOf(pace, weekly),
        xLabels = labels,
    )
}

private const val MIDDLE_LABEL_MIN_WEEKS = 4
private const val MIN_BAND_POINTS = 3

/** The 95 % band of the slope around the weekly centre of mass, from the first to the last weekly point only. */
private fun bandOf(pace: WeightPace, weekly: List<WeightPacePoint>): List<WeightPaceBandPoint> {
    val low = pace.slopeLowKgWeek
    val high = pace.slopeHighKgWeek
    if (low == null || high == null || weekly.size < MIN_BAND_POINTS) return emptyList()
    val meanX = weekly.map { it.x.toDouble() }.average()
    val meanKg = weekly.map { it.kg }.average()
    return listOf(weekly.first().x, weekly.last().x).map { x ->
        val a = meanKg + low * (x - meanX)
        val b = meanKg + high * (x - meanX)
        WeightPaceBandPoint(x, min(a, b), max(a, b))
    }
}

/** "Ritmo / Objetivo" with a key for each chart line (the chart's legend, C-132). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaceFigures(plan: CoachPlan, pace: WeightPace?) {
    // Without enough data only the plan's rate shows (07 §5 (5)).
    val actual = pace?.takeIf { it.slopeKgWeek != null && it.state != WeightPaceState.NoData }
    val slope = actual?.slopeKgWeek
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (actual != null && slope != null) {
            val provisional = actual.state == WeightPaceState.Soon
            PaceFigure(
                text = stringResource(
                    if (provisional) {
                        Res.string.trainer_plan_pace_actual_provisional
                    } else {
                        Res.string.trainer_plan_pace_actual
                    },
                    signedHundredths(slope),
                ),
                color = AppTheme.colors.athleticCyan,
                dashed = false,
                tag = "trainer_box_plan_pace_actual",
            )
        }
        PaceFigure(
            text = stringResource(Res.string.trainer_plan_pace_goal, signedHundredths(plan.nutrition.rateKgWeek)),
            color = AppTheme.colors.textSecondary,
            dashed = true,
            tag = "trainer_box_plan_pace_goal",
        )
    }
}

@Composable
private fun PaceFigure(text: String, color: Color, dashed: Boolean, tag: String) {
    Row(
        Modifier.testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            val stroke = Modifier.height(Spacing.xs / 2).background(color)
            if (dashed) {
                Box(stroke.width(Spacing.xs * KEY_DASH_FACTOR))
                Box(stroke.width(Spacing.xs * KEY_DASH_FACTOR))
            } else {
                Box(stroke.width(Spacing.lg))
            }
        }
        Text(text = text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.textPrimary)
    }
}

/** The verdict sentence of `07` §5 (5): up to three lines, never a chip, icon or colour (C-133). */
@Composable
private fun PaceVerdict(pace: WeightPace) {
    val cut = pace.planRateKgWeek < 0.0
    val slope = pace.slopeKgWeek ?: 0.0
    val text = when (pace.state) {
        WeightPaceState.NoData -> stringResource(Res.string.trainer_plan_verdict_no_data)
        WeightPaceState.OnPace -> stringResource(Res.string.trainer_plan_verdict_on_pace)
        WeightPaceState.Slower -> stringResource(slowerVerdict(cut, slope))
        WeightPaceState.Faster -> stringResource(
            if (cut) Res.string.trainer_plan_verdict_cut_faster else Res.string.trainer_plan_verdict_gain_faster,
        )
        WeightPaceState.Stable -> stringResource(Res.string.trainer_plan_verdict_stable)
        WeightPaceState.Rising ->
            stringResource(Res.string.trainer_plan_verdict_rising, AppNumberFormat.decimal(abs(slope), 2))
        WeightPaceState.Falling ->
            stringResource(Res.string.trainer_plan_verdict_falling, AppNumberFormat.decimal(abs(slope), 2))
        // Too early to judge: the provisional figure says it, so there is no verdict line.
        WeightPaceState.Soon -> return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.testTag("trainer_box_plan_verdict"),
        )
        if (pace.earlyCutNote) {
            Text(
                text = stringResource(Res.string.trainer_plan_verdict_early_cut),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.testTag("trainer_box_plan_verdict_early"),
            )
        }
    }
}

/** Never "pierdes" when the weight is not falling (the `floor_limited` lesson of `07` §12.7). */
private fun slowerVerdict(cut: Boolean, slope: Double) = when {
    cut && slope < 0.0 -> Res.string.trainer_plan_verdict_cut_slower
    cut -> Res.string.trainer_plan_verdict_cut_not_falling
    slope > 0.0 -> Res.string.trainer_plan_verdict_gain_slower
    else -> Res.string.trainer_plan_verdict_gain_not_rising
}

/**
 * The Calorías tile (the mean per logged diary day since the plan started against the target) and its scope line,
 * always shown so the figure says what it covers; "sin datos" only when no day is logged. Cardio and steps live only in the "Tu bloque" card. Opens nothing.
 */
@Composable
private fun PlanTiles(plan: CoachPlan, diaryCalories: DiaryCalories?) {
    val avgKcal = diaryCalories?.avgKcal
    val targetKcal = plan.nutrition.targetKcal
    val calories = TileData(
        label = stringResource(Res.string.trainer_plan_tile_calories),
        value = avgKcal?.let { AppNumberFormat.integer(it) } ?: NO_VALUE,
        sub = if (avgKcal != null) {
            stringResource(Res.string.trainer_plan_tile_of_kcal, AppNumberFormat.integer(targetKcal))
        } else {
            stringResource(Res.string.trainer_plan_tile_no_data)
        },
        ratio = avgKcal?.let { it / targetKcal.toDouble() },
        tag = "trainer_box_plan_tile_calories",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        PlanTile(calories, 0, Modifier.fillMaxWidth())
        Text(
            text = if (diaryCalories == null) {
                stringResource(Res.string.trainer_plan_tiles_caption_empty)
            } else {
                pluralStringResource(
                    Res.plurals.trainer_plan_tiles_caption,
                    diaryCalories.days,
                    diaryCalories.days,
                )
            },
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.testTag("trainer_box_plan_tiles_caption"),
        )
    }
}

private class TileData(val label: String, val value: String, val sub: String, val ratio: Double?, val tag: String)

@Composable
private fun PlanTile(tile: TileData, index: Int, modifier: Modifier) {
    val percent = tile.ratio?.let { (it * PERCENT).roundToInt() }
    MetricTile(
        label = tile.label,
        value = tile.value,
        modifier = modifier.heightIn(min = TileMinHeight).testTag(tile.tag),
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = tile.sub,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    percent?.let {
                        Text(
                            text = NonBreaking.join(it.toString(), "%"),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                }
                AppLinearProgress(
                    fraction = (tile.ratio ?: 0.0).toFloat(),
                    stateDescription = "${tile.label} ${tile.value} ${tile.sub}",
                    staggerIndex = TILE_STAGGER + index,
                )
            }
        },
    )
}
