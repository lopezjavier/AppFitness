package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_cd
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_headline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_cardio_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_goal_eyebrow
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_fact_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_rate_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_kcal_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_kcal_target
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppShapes
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.GoalGraphic
import io.github.lopezjavier.appfitness.designsystem.MacroLine
import io.github.lopezjavier.appfitness.designsystem.MacroStackedBar
import io.github.lopezjavier.appfitness.designsystem.MacroStackedBarDefaults
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.NonBreaking
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.designsystem.SummaryFact
import io.github.lopezjavier.appfitness.domain.model.PlanNutrition
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.feature.weight.signedTenths
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private val GoalBandMinHeight = 72.dp
private const val RATE_EPSILON = 0.05
private const val GRAPHIC_STAGGER = 1
internal const val BLOCK_SEGMENTS_STAGGER = 2
private const val CARDIO_STAGGER = 3
private const val STEPS_STAGGER = 4
private const val SUMMARY_STAGGER = 5

/**
 * The goal band of "Tu plan" (C-125): the "OBJETIVO" eyebrow, the goal name with its weekly rate (omitted at zero) and
 * the decorative [GoalGraphic]. The graphic is hidden rather than wrapping the name (C-126).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GoalBand(phase: Phase, rateKgWeek: Double) {
    val name = stringResource(PhaseText.label(phase))
    var graphicShown by remember(name) { mutableStateOf(true) }
    Row(
        Modifier
            .padding(top = Spacing.xs)
            .fillMaxWidth()
            .heightIn(min = GoalBandMinHeight)
            .background(AppTheme.colors.surfaceControl, AppShapes.md)
            .padding(Spacing.md)
            .testTag("trainer_box_plan_goal"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.trainer_goal_eyebrow).uppercase(),
                style = AppTheme.typography.labelTechnical,
                color = AppTheme.colors.textSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleLarge,
                    color = AppTheme.colors.textPrimary,
                    onTextLayout = { if (it.lineCount > 1) graphicShown = false },
                )
                if (abs(rateKgWeek) >= RATE_EPSILON) {
                    Text(
                        text = NonBreaking.join(
                            signedTenths(rateKgWeek),
                            stringResource(Res.string.trainer_plan_rate_unit),
                        ),
                        style = AppTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                        color = AppTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = Spacing.xs).testTag("trainer_box_plan_rate"),
                    )
                }
            }
        }
        if (graphicShown) GoalGraphic(phase, staggerIndex = GRAPHIC_STAGGER)
    }
}

/** The macro bar of "Tu plan" (8 dp, kcal shares) with its text 4 dp below (C-125). */
@Composable
internal fun PlanMacros(nutrition: PlanNutrition) {
    val (protein, carbs, fat) = MacroStackedBarDefaults.kcalShares(
        nutrition.proteinG.toDouble(),
        nutrition.carbsG.toDouble(),
        nutrition.fatG.toDouble(),
    )
    Column(Modifier.padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MacroStackedBar(
            proteinPct = protein,
            carbsPct = carbs,
            fatPct = fat,
            testTag = "trainer_box_plan_macro_bar",
            barHeight = MacroStackedBarDefaults.SlimHeight,
            staggerIndex = 0,
        )
        MacroLine(
            proteinG = AppNumberFormat.integer(nutrition.proteinG),
            carbsG = AppNumberFormat.integer(nutrition.carbsG),
            fatG = AppNumberFormat.integer(nutrition.fatG),
            modifier = Modifier.testTag("trainer_box_plan_macros"),
            style = AppTheme.typography.bodyMedium,
        )
    }
}

/**
 * The "Cardio" and "Pasos al día" progress facts of [week] (C-124): minutes done of the target and the mean steps of
 * the target, each with a 4 dp bar clamped at 100 %. A row without a target is omitted; without step data the row
 * reads "Sin datos de pasos" and has no bar. Empty without a week.
 */
@Composable
internal fun weekActivityFacts(week: PlanWeek?): List<SummaryFact> {
    if (week == null) return emptyList()
    val cardio = week.cardioMinTarget?.takeIf { it > 0 }?.let { target ->
        SummaryFact(
            label = stringResource(Res.string.trainer_plan_fact_cardio),
            value = stringResource(
                Res.string.trainer_block_cardio_value,
                AppNumberFormat.integer(week.cardioMinDone),
                AppNumberFormat.integer(target),
            ),
            fraction = week.cardioMinDone.toFloat() / target,
            staggerIndex = CARDIO_STAGGER,
        )
    }
    val steps = week.stepsTarget?.takeIf { it > 0 }?.let { target ->
        val label = stringResource(Res.string.trainer_block_steps_label)
        val avg = week.stepsAvg
        if (avg == null) {
            SummaryFact(label, stringResource(Res.string.trainer_block_steps_none), muted = true)
        } else {
            SummaryFact(
                label = label,
                value = stringResource(
                    Res.string.trainer_block_steps_value,
                    AppNumberFormat.integer(avg),
                    AppNumberFormat.integer(target),
                ),
                fraction = avg.toFloat() / target,
                staggerIndex = STEPS_STAGGER,
            )
        }
    }
    return listOfNotNull(cardio, steps)
}

/** Session segments with the spoken "3 de 4 entrenos" (C-124, C-127). */
@Composable
internal fun SessionSegments(done: Int, planned: Int, tag: String, staggerOffset: Int) {
    if (planned <= 0) return
    SegmentedProgress(
        total = planned,
        done = done.coerceAtMost(planned),
        stateDescription = stringResource(Res.string.report_sessions_cd, done, planned),
        modifier = Modifier.padding(top = Spacing.xs),
        testTag = tag,
        staggerOffset = staggerOffset,
    )
}

/**
 * The "Resumen semanal" box (C-127): the last finished week's workouts as a headline with segments, its cardio and
 * steps facts and, when the summary has them, the mean weight and calories as two tiles. A paused week reads only
 * "Semana en pausa".
 */
@Composable
internal fun WeekSummaryBox(summary: WeekSummary?, lastWeek: PlanWeek?, onOpen: () -> Unit) {
    val paused = summary?.paused == true
    val done = if (summary != null && !paused) summary.sessionsDone else lastWeek?.completed
    val planned = if (summary != null && !paused) summary.sessionsPlanned else lastWeek?.planned
    val showHeadline = !paused && done != null && planned != null && planned > 0
    SummaryCard(
        title = stringResource(Res.string.week_summary_title),
        lines = if (paused) listOf(stringResource(Res.string.block_week_paused)) else emptyList(),
        onClick = onOpen,
        testTag = "trainer_box_week_summary",
        headline = if (showHeadline) stringResource(Res.string.report_sessions_headline, done ?: 0, planned ?: 0) else null,
        headlineUnit = if (showHeadline) stringResource(Res.string.trainer_summary_unit) else null,
        facts = if (paused) emptyList() else weekActivityFacts(lastWeek),
        progress = if (showHeadline) {
            { SessionSegments(done ?: 0, planned ?: 0, "trainer_box_week_summary_progress", SUMMARY_STAGGER) }
        } else {
            null
        },
        footer = summary?.takeIf { !paused }?.let { { SummaryTiles(it) } },
    )
}

/** "Peso medio" and "Calorías" as two tiles of equal height; a figure the summary lacks is left out. */
@Composable
private fun SummaryTiles(summary: WeekSummary) {
    val weight = summary.weight?.avgKg
    val nutrition = summary.nutrition?.takeIf { it.gateOk }
    val kcal = nutrition?.avgKcal
    if (weight == null && kcal == null) return
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.sm).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (weight != null) {
            MetricTile(
                label = stringResource(Res.string.trainer_summary_weight_label),
                value = AppNumberFormat.decimal(weight),
                unit = "kg",
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("trainer_box_week_summary_weight"),
            )
        }
        if (kcal != null) {
            MetricTile(
                label = stringResource(Res.string.trainer_summary_kcal_label),
                value = AppNumberFormat.integer(kcal),
                unit = "kcal",
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("trainer_box_week_summary_kcal"),
                supporting = nutrition.targetKcal?.let { target ->
                    {
                        Text(
                            text = stringResource(
                                Res.string.trainer_summary_kcal_target,
                                AppNumberFormat.integer(target),
                            ),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colors.textSecondary,
                        )
                    }
                },
            )
        }
    }
}
