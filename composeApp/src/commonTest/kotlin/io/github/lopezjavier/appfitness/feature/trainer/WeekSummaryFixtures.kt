package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NutritionDiagnosis
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryDecision
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryNutrition
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryWeight
import kotlinx.datetime.LocalDate

/** A summary of week 2 (3-9 Nov 2026) with every figure of the Gherkin "summary of a finished week". */
internal fun weekSummary(
    seen: Boolean = false,
    groups: List<GroupSets> = listOf(
        GroupSets(MuscleGroup.Chest, 10.0),
        GroupSets(MuscleGroup.Back, 12.0),
        GroupSets(MuscleGroup.Triceps, 2.5),
        GroupSets(MuscleGroup.Calves, 0.0),
    ),
    weight: WeekSummaryWeight? = WeekSummaryWeight(80.2, -0.3, -0.5, 0.0, -0.4),
    nutrition: WeekSummaryNutrition? = weekNutrition(),
    changes: List<CoachChange> = emptyList(),
    deload: Boolean = false,
    stalls: List<StallDiagnosis> = emptyList(),
) = WeekSummary(
    planId = "plan-1",
    week = 2,
    startDate = LocalDate(2026, 11, 3),
    endDate = LocalDate(2026, 11, 9),
    seen = seen,
    sessionsDone = 3,
    sessionsPlanned = 4,
    groups = groups,
    weight = weight,
    completeDays = nutrition?.completeDays ?: 0,
    weighInDays = 3,
    changes = changes,
    deload = deload,
    stalls = stalls,
    nutrition = nutrition,
)

/** The nutrition section of a week with 5 complete days (gate on) by default. */
internal fun weekNutrition(
    completeDays: Int = 5,
    gateOk: Boolean = completeDays >= 5,
    avgKcal: Int? = if (gateOk) 2310 else null,
    avgProteinG: Double? = if (gateOk) 148.5 else null,
    targetKcal: Int? = if (gateOk) 2400 else null,
    targetProteinG: Int? = if (gateOk) 150 else null,
    proteinGPerKg: Double? = if (gateOk) 1.8 else null,
    decisions: List<WeekSummaryDecision> = emptyList(),
    diagnosis: NutritionDiagnosis? = null,
) = WeekSummaryNutrition(
    diagnosis = diagnosis,
    completeDays = completeDays,
    gateOk = gateOk,
    avgKcal = avgKcal,
    avgProteinG = avgProteinG,
    targetKcal = targetKcal,
    targetProteinG = targetProteinG,
    proteinGPerKg = proteinGPerKg,
    decisions = decisions,
)

internal fun weekDecision(status: ProposalStatus = ProposalStatus.Applied, id: String = "prop-1") = WeekSummaryDecision(
    CalorieProposal(id, "plan-1", 2400, 2500, 4.0, "rate_outside_interval", -0.3, -0.5, 0.0, -0.4),
    status,
)
