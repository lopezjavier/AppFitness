package io.github.lopezjavier.appfitness.domain.usecase

import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockContext
import io.github.lopezjavier.appfitness.domain.coach.builder.NextBlockTargets
import io.github.lopezjavier.appfitness.domain.coach.builder.SplitCatalog
import io.github.lopezjavier.appfitness.domain.model.BiometricBounds
import io.github.lopezjavier.appfitness.domain.model.BlockReview
import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.CoachException
import io.github.lopezjavier.appfitness.domain.model.CoachFailure
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase
import io.github.lopezjavier.appfitness.domain.model.PhaseDecision
import io.github.lopezjavier.appfitness.domain.port.Clock
import io.github.lopezjavier.appfitness.domain.repository.CoachBlockRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachPlanRepository
import io.github.lopezjavier.appfitness.domain.repository.CoachReviewRepository
import io.github.lopezjavier.appfitness.domain.repository.DiscomfortRepository
import io.github.lopezjavier.appfitness.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * Reads what the next block's builder needs after the end-of-block review (US-102): the review figures, the ended
 * block, the phase of the next block, the open discomforts and the age. Reads only; no model call.
 */
class LoadNextBlockContextUseCase(
    private val reviews: CoachReviewRepository,
    private val blocks: CoachBlockRepository,
    private val plans: CoachPlanRepository,
    private val profile: ProfileRepository,
    private val discomforts: DiscomfortRepository,
    private val clock: Clock,
) {
    /**
     * [previousPlanId] must be the plan of the review that just closed, or of the early review of "Bloque nuevo" when
     * none is stored yet; any other fails with `Conflict`.
     */
    @Suppress("TooGenericExceptionCaught") // Every failed read becomes the Result; cancellation is rethrown.
    suspend operator fun invoke(previousPlanId: String): Result<NextBlockContext> = try {
        Result.success(load(previousPlanId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun load(previousPlanId: String): NextBlockContext {
        val today = clock.today()
        // Mid-block there is no stored review yet: "Bloque nuevo" reads the early one (computed, null id).
        val review = reviews.openReview(today).getOrThrow() ?: reviews.openNewBlock(today).getOrThrow()?.review
        if (review == null || review.planId != previousPlanId) throw CoachException(CoachFailure.Conflict)
        val block = blocks.currentBlock(previousPlanId).getOrThrow() ?: throw CoachException(CoachFailure.Conflict)
        val nextPhase = nextPhase(review).getOrThrow()
        val birthDate = profile.getBiometrics().getOrThrow().birthDate
        val exerciseIds = block.sessions.flatMap { s ->
            s.exercises.filter { !it.isCardio }.map { it.exerciseId }
        }.toSet()
        val open = discomforts.unresolved(exerciseIds).getOrThrow()
            .filter { DiscomfortStatus.isOpen(it, clock.now()) }.map { it.exerciseId }.toSet()

        val groupOf = block.sessions.flatMap { it.exercises }
            .mapNotNull { e -> groupOfKey(e.muscleGroup)?.let { e.exerciseId to it } }.toMap()
        val stalledLifts = review.lifts.filter { it.stalled }.map { it.exerciseId }.toSet()
        val stalledGroups = (stalledLifts + review.stalls.map { it.exerciseId }).mapNotNull { groupOf[it] }.toSet()
        return NextBlockContext(
            previousPlanId = previousPlanId,
            previousBlock = block,
            adherencePct = review.adherence.pct,
            baseSets = baseSets(block, review),
            stalledLifts = stalledLifts,
            stalledGroups = stalledGroups,
            discomfortGroups = open.mapNotNull { groupOf[it] }.toSet(),
            nextPhase = nextPhase,
            age = birthDate?.let { BiometricBounds.ageOn(it, today) },
        )
    }

    /** The accepted phase change, else the phase of the plan the next block belongs to. */
    private suspend fun nextPhase(review: BlockReview): Result<Phase> {
        val accepted = review.phaseProposal?.takeIf { review.phaseDecision == PhaseDecision.Accepted }
        if (accepted != null) return Result.success(accepted.toPhase)
        return plans.currentPlan().map { it?.phase ?: Phase.Maintenance }
    }

    /** The ended block's weekly sets per group, else what the review measured, else the default 10. */
    private fun baseSets(block: CoachBlock, review: BlockReview): Map<MuscleGroup, Int> {
        val measured = review.groups.associate { it.muscleGroup to it.sets.roundToInt() }
        return SplitCatalog.ALL_GROUPS.associateWith { g ->
            (block.training.weeklySets[g.name.lowercase()] ?: measured[g] ?: NextBlockTargets.DEFAULT_BASE)
                .coerceIn(0, NextBlockTargets.CAP)
        }
    }

    private fun groupOfKey(key: String): MuscleGroup? = MuscleGroup.entries.firstOrNull { it.name.lowercase() == key }
}
