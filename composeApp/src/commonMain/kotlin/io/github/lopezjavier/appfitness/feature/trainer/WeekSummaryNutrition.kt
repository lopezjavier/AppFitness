package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_days_label
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_days_unit
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_kcal_label
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_kcal_target
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_protein_label
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_protein_per_kg
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_protein_target
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_weight_label
import io.github.lopezjavier.appfitness.composeapp.resources.report_tile_weight_slope
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_decision
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_missing
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal_applied
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal_declined
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_proposal_pending
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weigh_ins
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight_avg_only
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight_no_target
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_weight_none
import io.github.lopezjavier.appfitness.designsystem.AppNumberFormat
import io.github.lopezjavier.appfitness.designsystem.AppTheme
import io.github.lopezjavier.appfitness.designsystem.SectionCard
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.designsystem.StatRing
import io.github.lopezjavier.appfitness.designsystem.WeekStrip
import io.github.lopezjavier.appfitness.designsystem.WeekStripDay
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryDecision
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryNutrition
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * The nutrition card (07 section 4 (a)): the four figures as a 2 x 2 tile grid, weigh-ins and the calorie decisions.
 */
@Composable
internal fun NutritionSection(summary: WeekSummary) {
    val nutrition = summary.nutrition ?: return
    SectionCard(
        stringResource(Res.string.week_summary_nutrition_header),
        testTag = "week_summary_section_nutrition",
    ) {
        if (!nutrition.gateOk) {
            SummaryLine(
                stringResource(Res.string.week_summary_nutrition_missing, nutrition.completeDays),
                Modifier.testTag("week_summary_nutrition_missing"),
            )
        }
        DaysLoggedRing(nutrition.completeDays)
        val tiles = nutritionTiles(summary, nutrition)
        if (tiles.isNotEmpty()) ReportTileGrid(tiles)
        if (summary.weight?.avgKg == null) {
            SummaryLine(weightText(summary), Modifier.testTag("week_summary_nutrition_weight"))
        }
        SummaryLine(
            stringResource(
                Res.string.week_summary_nutrition_weigh_ins,
                pluralStringResource(Res.plurals.week_summary_weigh_ins, summary.weighInDays, summary.weighInDays),
            ),
            Modifier.testTag("week_summary_nutrition_weigh_ins"),
        )
        nutrition.decisions.forEach { DecisionLine(it) }
    }
}

private const val WEEK_DAYS = 7

/** The days with complete records as a ring of the seven days of the week, with the count in its centre. */
@Composable
private fun DaysLoggedRing(days: Int) {
    val shown = days.coerceIn(0, WEEK_DAYS)
    val label = stringResource(
        Res.string.report_tile_days_label,
    ) + ": " + shown + "/" + WEEK_DAYS
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatRing(
            fraction = shown.toFloat() / WEEK_DAYS,
            color = AppTheme.colors.neonLime,
            modifier = Modifier.testTag("week_summary_nutrition_days_ring"),
        ) {
            Text(
                text = "$shown/$WEEK_DAYS",
                style = AppTheme.typography.labelNumeric,
                color = AppTheme.colors.textPrimary,
            )
        }
        WeekStrip(
            days = (1..WEEK_DAYS).map { WeekStripDay(label = it.toString(), done = it <= shown) },
            modifier = Modifier.weight(1f),
            color = AppTheme.colors.neonLime,
        )
    }
}

/** Days, average calories, average protein and average weight; a figure the week lacks is left out. */
@Composable
private fun nutritionTiles(summary: WeekSummary, nutrition: WeekSummaryNutrition): List<ReportTile> = buildList {
    if (nutrition.gateOk) {
        add(
            ReportTile(
                "week_summary_nutrition_days",
                stringResource(Res.string.report_tile_days_label),
                nutrition.completeDays.toString(),
                stringResource(Res.string.report_tile_days_unit),
            ),
        )
        nutrition.avgKcal?.let { kcal ->
            val target = nutrition.targetKcal
            add(
                ReportTile(
                    "week_summary_nutrition_kcal",
                    stringResource(Res.string.report_tile_kcal_label),
                    AppNumberFormat.integer(kcal),
                    "kcal",
                    target?.let {
                        stringResource(
                            Res.string.report_tile_kcal_target,
                            AppNumberFormat.integer(it),
                            WeekSummaryText.kcalDifference(kcal, it),
                        )
                    },
                ),
            )
        }
        nutrition.avgProteinG?.let { protein ->
            val target = nutrition.targetProteinG?.let { AppNumberFormat.integer(it) }
            val perKg = nutrition.proteinGPerKg?.let {
                stringResource(Res.string.report_tile_protein_per_kg, WeekSummaryText.perKg(it))
            }
            add(
                ReportTile(
                    "week_summary_nutrition_protein",
                    stringResource(Res.string.report_tile_protein_label),
                    WeekSummaryText.proteinGrams(protein),
                    "g",
                    listOfNotNull(target?.let { stringResource(Res.string.report_tile_protein_target, it) }, perKg)
                        .joinToString(" · ").ifEmpty { null },
                ),
            )
        }
    }
    summary.weight?.avgKg?.let { avg ->
        add(
            ReportTile(
                "week_summary_nutrition_weight",
                stringResource(Res.string.report_tile_weight_label),
                WeekSummaryText.kg(avg),
                "kg",
                summary.weight?.slopeKgWeek?.let {
                    stringResource(Res.string.report_tile_weight_slope, WeekSummaryText.signedKg(it))
                },
            ),
        )
    }
}

@Composable
private fun DecisionLine(decision: WeekSummaryDecision) {
    val proposal = decision.proposal
    val status = stringResource(
        when (decision.status) {
            ProposalStatus.Pending -> Res.string.week_summary_proposal_pending
            ProposalStatus.Applied -> Res.string.week_summary_proposal_applied
            ProposalStatus.Declined -> Res.string.week_summary_proposal_declined
        },
    )
    val reason = stringResource(
        calorieProposalReason(proposal),
        AppNumberFormat.integer(abs(proposal.toKcal - proposal.fromKcal)),
    )
    SummaryLine(
        stringResource(Res.string.week_summary_decision, WeekSummaryText.kcalDelta(proposal), status, reason),
        Modifier.testTag("week_summary_nutrition_decision_${proposal.id}"),
    )
}

@Composable
private fun weightText(summary: WeekSummary): String {
    val weight = summary.weight
    val avg = weight?.avgKg
    val slope = weight?.slopeKgWeek
    val target = weight?.targetRateKgWeek
    return when {
        avg == null -> stringResource(Res.string.week_summary_weight_none)

        slope == null -> stringResource(Res.string.week_summary_weight_avg_only, WeekSummaryText.kg(avg))

        target == null -> stringResource(
            Res.string.week_summary_weight_no_target,
            WeekSummaryText.kg(avg),
            WeekSummaryText.signedKg(slope),
        )

        else -> stringResource(
            Res.string.week_summary_weight,
            WeekSummaryText.kg(avg),
            WeekSummaryText.signedKg(slope),
            WeekSummaryText.signedKg(target),
        )
    }
}
