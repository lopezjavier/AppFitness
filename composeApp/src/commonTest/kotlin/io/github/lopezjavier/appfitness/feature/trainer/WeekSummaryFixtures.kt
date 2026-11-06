package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.CalorieProposal
import io.github.lopezjavier.appfitness.domain.model.CoachChange
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.ProposalStatus
import io.github.lopezjavier.appfitness.domain.model.WeekSummary
import io.github.lopezjavier.appfitness.domain.model.WeekSummaryProposal
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
    missingRecords: Boolean = false,
    changes: List<CoachChange> = emptyList(),
    calorieProposal: WeekSummaryProposal? = null,
    deload: Boolean = false,
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
    completeDays = if (missingRecords) 4 else 5,
    weighInDays = if (missingRecords) 1 else 3,
    missingRecords = missingRecords,
    changes = changes,
    calorieProposal = calorieProposal,
    deload = deload,
)

internal fun weekProposal(status: ProposalStatus = ProposalStatus.Applied) = WeekSummaryProposal(
    CalorieProposal("prop-1", "plan-1", 2400, 2500, 4.0, "rate_outside_interval", -0.3, -0.5, 0.0, -0.4),
    status,
)
