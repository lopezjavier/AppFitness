@file:Suppress("TooManyFunctions") // One card: its header, chart and verdict are private pieces of it.

package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flag
import io.github.lopezjavier.appfitness.composeapp.resources.ic_flame_outlined
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_figure_date
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_figure_goal
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_figure_now
import io.github.lopezjavier.appfitness.composeapp.resources.px_trainer_plan_key_goal
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_eyebrow
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_calories_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_calories_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_goal
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_today
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_today_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_chart_week
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pace_actual
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_pace_actual_provisional
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_rate_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_section
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_tile_calories
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
import io.github.lopezjavier.appfitness.composeapp.resources.unit_kg
import io.github.lopezjavier.appfitness.designsystem.AppBorder
import io.github.lopezjavier.appfitness.designsystem.AppDateFormat
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppPaint
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.IconBadge
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val CENTS = 100
private const val RATE_EPSILON = 0.005
private const val WEEK_CENTRE = 0.5f
private const val MIN_LABEL_GAP_WEEKS = 1.5f
private const val MINUS = '−'
private const val TILE_STAGGER = 1
private const val KEY_DASH_FACTOR = 1.5f
private const val KEY_DASHES = 3

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
 * The accepted plan's card on the Trainer root (C-131..C-138, R-102, R-105): the goal header, the figures strip, the
 * weight pace chart and its key, then the phase's reason with its "?", the verdict, the weeks in the phase and the
 * Calorías row. The card
 * opens nothing (the "Tu plan" screen is gone): its actions are in the ⋮ [menu] of the title row.
 */
@Suppress("LongParameterList") // The plan, the figures around it and the card's menu.
@Composable
internal fun PlanCard(
    plan: CoachPlan,
    pace: WeightPace?,
    weeksInPhase: PhaseWeeks?,
    diaryCalories: DiaryCalories?,
    modifier: Modifier = Modifier,
    menu: (@Composable () -> Unit)? = null,
) {
    SummaryCard(
        title = stringResource(Res.string.trainer_plan_section),
        lines = emptyList(),
        onClick = null,
        testTag = "trainer_box_plan",
        modifier = modifier,
        titleAction = menu,
        glow = AppTheme.colors.athleticCyan,
        content = { PlanChartPanel(plan, pace) },
        footer = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PhaseReasonLine(plan)
                if (pace != null) PaceVerdict(pace)
                weeksInPhase?.takeIf { it.weeks > 0 }?.let { WeeksInPhaseLine(it) }
                CaloriesRow(plan, diaryCalories)
            }
        },
    )
}

private fun hasChart(pace: WeightPace): Boolean = pace.planLine != null || pace.weekly.isNotEmpty()

/**
 * The plan's content (C-131): the goal header with the planned rate chip, the figures strip (now, goal weight and
 * deadline), the weight pace chart and its key. No inner panel: the card's own cyan glow is the hero light.
 */
@Composable
private fun PlanChartPanel(plan: CoachPlan, pace: WeightPace?) {
    Column(
        Modifier
            .padding(top = Spacing.xs)
            .fillMaxWidth()
            .testTag("trainer_box_plan_goal"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        GoalHeader(plan)
        PlanFigures(plan, pace)
        if (pace != null && hasChart(pace)) {
            WeightPaceChart(
                data = chartDataOf(pace),
                valueLabel = { AppNumberFormat.decimal(it) },
                modifier = Modifier.testTag("trainer_box_plan_chart"),
            )
            // The key explains the chart's lines: without a chart the rate chip already says it (R-102).
            PaceFigures(pace)
        }
    }
}

/** Flag badge, "OBJETIVO" eyebrow and the phase name, with the planned rate chip at the end. */
@Composable
private fun GoalHeader(plan: CoachPlan) {
    val rate = plan.nutrition.rateKgWeek
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Res.drawable.ic_flag, AppTheme.colors.athleticCyan)
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
        if (abs(rate) >= RATE_EPSILON) {
            StatusChip(
                text = NonBreaking.join(signedHundredths(rate), stringResource(Res.string.trainer_plan_rate_unit)),
                tone = StatusTone.Info,
                modifier = Modifier.testTag("trainer_box_plan_rate"),
            )
        }
    }
}

/**
 * The figures strip: "Ahora" (the latest weekly average, in cyan like the trend), "Meta" (the goal weight) and "Fecha"
 * (the deadline), split by hairlines on a raised tile. A figure without data is left out; no figure, no strip.
 */
@Composable
private fun PlanFigures(plan: CoachPlan, pace: WeightPace?) {
    val now = pace?.weekly?.lastOrNull()?.kg
    val outcome = plan.weightOutcome
    val kg = stringResource(Res.string.unit_kg)
    val figures = buildList {
        now?.let {
            add(PlanFigure(Res.string.px_trainer_plan_figure_now, AppNumberFormat.decimal(it), kg, accent = true))
        }
        outcome?.let {
            add(PlanFigure(Res.string.px_trainer_plan_figure_goal, AppNumberFormat.decimal(it.targetValue), kg))
            add(PlanFigure(Res.string.px_trainer_plan_figure_date, AppDateFormat.shortDate(it.deadline), null))
        }
    }
    if (figures.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(AppShapes.md)
            .background(AppPaint.tileSurface(AppTheme.colors))
            .border(AppBorder.thin, AppPaint.tileBorder(), AppShapes.md)
            .padding(vertical = Spacing.sm)
            .testTag("trainer_box_plan_meta"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        figures.forEachIndexed { index, figure ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(AppBorder.thin)
                        .fillMaxHeight()
                        .background(AppTheme.colors.divider),
                )
            }
            PlanFigureCell(figure, Modifier.weight(1f))
        }
    }
}

private class PlanFigure(val label: StringResource, val value: String, val unit: String?, val accent: Boolean = false)

@Composable
private fun PlanFigureCell(figure: PlanFigure, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(horizontal = Spacing.sm)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(figure.label).uppercase(),
            style = AppTheme.typography.labelTechnical,
            color = AppTheme.colors.textSecondary,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Bottom) {
            Text(
                text = figure.value,
                style = AppTheme.typography.labelNumeric,
                color = if (figure.accent) AppTheme.colors.athleticCyan else AppTheme.colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
            figure.unit?.let {
                Text(text = it, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
            }
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
private fun PaceFigures(pace: WeightPace?) {
    // Without enough data only the plan's rate shows (07 §5 (5)).
    val actual = pace?.takeIf { it.slopeKgWeek != null && it.state != WeightPaceState.NoData }
    val slope = actual?.slopeKgWeek
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
        // The dashed line is the flat goal weight, so its key names the goal, not a rate.
        pace?.planLine?.let {
            PaceFigure(
                text = stringResource(Res.string.px_trainer_plan_key_goal, AppNumberFormat.decimal(it.endKg)),
                color = AppTheme.colors.textSecondary,
                dashed = true,
                tag = "trainer_box_plan_pace_goal",
            )
        }
    }
}

/** One key of the chart: the line's swatch (solid or dashed) and its figure, with no pill around it. */
@Composable
private fun PaceFigure(text: String, color: Color, dashed: Boolean, tag: String) {
    Row(
        Modifier.testTag(tag).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs / 2)) {
            val stroke = Modifier.height(Spacing.xs / 2).clip(AppShapes.full).background(color)
            if (dashed) {
                repeat(KEY_DASHES) { Box(stroke.width(Spacing.xs * KEY_DASH_FACTOR)) }
            } else {
                Box(stroke.width(Spacing.lg))
            }
        }
        Text(text = text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
}

/** The verdict sentence of `07` §5 (5): up to three lines, never a chip, icon or colour (C-133). */
@Composable
internal fun PaceVerdict(pace: WeightPace, testTag: String = "trainer_box_plan_verdict") {
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
            modifier = Modifier.testTag(testTag),
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
 * The Calorías row (05 §6.0g): a [GoalBarRow] like Cardio and Pasos al día, "2.122 de 2.507 kcal" (the mean per logged
 * diary day since the plan started, then the target) over an always-cyan 8 dp bar; no percentage, no state word, no
 * glyph. Without any logged day it reads "Sin datos de comidas" with no bar. Opens nothing.
 */
@Composable
private fun CaloriesRow(plan: CoachPlan, diaryCalories: DiaryCalories?) {
    val target = plan.nutrition.targetKcal
    val avg = diaryCalories?.avgKcal
    GoalBarRow(
        label = stringResource(Res.string.trainer_plan_tile_calories),
        value = if (avg == null) {
            stringResource(Res.string.trainer_plan_calories_none)
        } else {
            stringResource(
                Res.string.trainer_plan_calories_value,
                AppNumberFormat.integer(avg),
                AppNumberFormat.integer(target),
            )
        },
        ratio = avg?.takeIf { target > 0 }?.let { it.toFloat() / target },
        pace = null,
        stagger = TILE_STAGGER,
        tag = "trainer_box_plan_tile_calories",
        icon = Res.drawable.ic_flame_outlined,
    )
}
