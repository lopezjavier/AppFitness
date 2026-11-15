package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.block_week_paused
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_cd
import io.github.lopezjavier.appfitness.composeapp.resources.report_sessions_headline
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_cardio_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_none
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_block_steps_value
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_plan_fact_cardio
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_kcal_label
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_kcal_target
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_unit
import io.github.lopezjavier.appfitness.composeapp.resources.trainer_summary_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_title
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.MetricTile
import io.github.lopezjavier.appfitness.designsystem.SegmentedProgress
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.SummaryCard
import io.github.lopezjavier.appfitness.designsystem.SummaryFact
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import org.jetbrains.compose.resources.stringResource

internal const val BLOCK_SEGMENTS_STAGGER = 2
private const val CARDIO_STAGGER = 3
private const val STEPS_STAGGER = 4
private const val SUMMARY_STAGGER = 5

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
