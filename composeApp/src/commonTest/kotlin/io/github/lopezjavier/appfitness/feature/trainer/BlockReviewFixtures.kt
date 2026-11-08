package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockAdherence
import io.github.lopezjavier.appfitness.domain.model.BlockLift
import io.github.lopezjavier.appfitness.domain.model.BlockLiftWeek
import io.github.lopezjavier.appfitness.domain.model.BlockOutcome
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.BlockWaist
import io.github.lopezjavier.appfitness.domain.model.BlockWeight
import io.github.lopezjavier.appfitness.domain.model.GoalSwitchPreview
import io.github.lopezjavier.appfitness.domain.model.GroupSets
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.NewBlockEntry
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseChangeReason
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.model.PhaseProposal
import io.github.lopezjavier.appfitness.domain.model.PhaseWeeks
import io.github.lopezjavier.appfitness.domain.model.StallDiagnosis
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import kotlinx.datetime.LocalDate

internal fun blockLift(
    exerciseId: String = "id-bench",
    first: Double? = 100.0,
    last: Double? = 105.0,
    stalled: Boolean = false,
) = BlockLift(
    exerciseId = exerciseId,
    weeks = if (first != null && last != null) listOf(BlockLiftWeek(1, first), BlockLiftWeek(3, last)) else emptyList(),
    firstE1rm = first,
    lastE1rm = last,
    changeKg = if (first != null && last != null) last - first else null,
    stalled = stalled,
)

internal fun cutLengthProposal() =
    PhaseProposal(Phase.Maintenance, PhaseChangeReason.CutLength, mapOf("deficit_weeks" to 12.0))

@Suppress("LongParameterList") // A fixture with one knob per review field.
internal fun blockReview(
    id: String = "review-1",
    planId: String = "plan-1",
    lifts: List<BlockLift> = listOf(blockLift()),
    groups: List<GroupSets> = listOf(GroupSets(MuscleGroup.Chest, 10.0)),
    weight: BlockWeight? = null,
    outcomes: List<BlockOutcome> = emptyList(),
    waist: BlockWaist? = null,
    stalls: List<StallDiagnosis> = emptyList(),
    phaseProposal: PhaseProposal? = cutLengthProposal(),
    phaseDecision: PhaseDecision? = null,
) = BlockReview(
    id = id,
    planId = planId,
    endedOn = LocalDate(2026, 10, 18),
    adherence = BlockAdherence(14, 16, 88),
    lifts = lifts,
    groups = groups,
    weight = weight,
    outcomes = outcomes,
    waist = waist,
    stalls = stalls,
    phaseProposal = phaseProposal,
    phaseDecision = phaseDecision,
)

/** Test double of [CoachReviewRepository]; an accepted or kept decision is stored like the server would. */
internal class FakeCoachReviewRepository(var review: Result<BlockReview?> = Result.success(null)) :
    CoachReviewRepository {
    var acceptResult: Result<String> = Result.success("plan-2")
    var keepResult: Result<Unit> = Result.success(Unit)
    var opens = 0
    val calls = mutableListOf<String>()

    override suspend fun openReview(today: LocalDate): Result<BlockReview?> {
        opens++
        return review
    }

    override suspend fun keepPhase(reviewId: String): Result<Unit> {
        calls += "keep:$reviewId"
        if (keepResult.isSuccess) review = review.map { it?.copy(phaseDecision = PhaseDecision.Kept) }
        return keepResult
    }

    override suspend fun acceptPhase(reviewId: String, today: LocalDate): Result<String> {
        calls += "accept:$reviewId"
        if (acceptResult.isSuccess) review = review.map { it?.copy(phaseDecision = PhaseDecision.Accepted) }
        return acceptResult
    }

    override suspend fun openNewBlock(today: LocalDate): Result<NewBlockEntry?> = Result.success(null)

    override suspend fun previewGoalSwitch(today: LocalDate): Result<GoalSwitchPreview> =
        Result.failure(UnsupportedOperationException())

    override suspend fun weeksInPhase(today: LocalDate): Result<PhaseWeeks?> = Result.success(null)
}
