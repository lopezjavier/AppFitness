package io.github.lopezjavier.appfitness.feature.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.lopezjavier.appfitness.composeapp.resources.Res
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_decision
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_days
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_header
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_kcal
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_kcal_avg_only
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_missing
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_protein
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_protein_avg_only
import io.github.lopezjavier.appfitness.composeapp.resources.week_summary_nutrition_protein_no_kg
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
import io.github.lopezjavier.appfitness.designsystem.ListSectionHeader
import io.github.lopezjavier.appfitness.designsystem.Spacing
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryDecision
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryNutrition
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** The nutrition card (07 section 4 (a)): days, averages against the goals, weight and the calorie decisions. */
@Composable
internal fun NutritionSection(summary: WeekSummary) {
    val nutrition = summary.nutrition ?: return
    SummaryCard("week_summary_section_nutrition") {
        ListSectionHeader(stringResource(Res.string.week_summary_nutrition_header))
        if (nutrition.gateOk) {
            NutritionFigures(nutrition)
        } else {
            SummaryLine(
                stringResource(Res.string.week_summary_nutrition_missing, nutrition.completeDays),
                Modifier.testTag("week_summary_nutrition_missing"),
            )
        }
        SummaryLine(weightText(summary), Modifier.testTag("week_summary_nutrition_weight"))
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

@Composable
private fun NutritionFigures(nutrition: WeekSummaryNutrition) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SummaryLine(
            stringResource(Res.string.week_summary_nutrition_days, nutrition.completeDays),
            Modifier.testTag("week_summary_nutrition_days"),
        )
        val kcal = nutrition.avgKcal
        if (kcal != null) {
            val target = nutrition.targetKcal
            val text = if (target == null) {
                stringResource(Res.string.week_summary_nutrition_kcal_avg_only, AppNumberFormat.integer(kcal))
            } else {
                stringResource(
                    Res.string.week_summary_nutrition_kcal,
                    AppNumberFormat.integer(kcal),
                    WeekSummaryText.kcalDifference(kcal, target),
                    AppNumberFormat.integer(target),
                )
            }
            SummaryLine(text, Modifier.testTag("week_summary_nutrition_kcal"))
        }
        val protein = nutrition.avgProteinG
        if (protein != null) {
            val avg = WeekSummaryText.proteinGrams(protein)
            val target = nutrition.targetProteinG?.let { AppNumberFormat.integer(it) }
            val perKg = nutrition.proteinGPerKg?.let { WeekSummaryText.perKg(it) }
            val text = when {
                target == null -> stringResource(Res.string.week_summary_nutrition_protein_avg_only, avg)
                perKg == null -> stringResource(Res.string.week_summary_nutrition_protein_no_kg, avg, target)
                else -> stringResource(Res.string.week_summary_nutrition_protein, avg, perKg, target)
            }
            SummaryLine(text, Modifier.testTag("week_summary_nutrition_protein"))
        }
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
