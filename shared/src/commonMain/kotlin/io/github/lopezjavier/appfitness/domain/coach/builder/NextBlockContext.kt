package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.CoachBlock
import io.github.lopezjavier.appfitness.domain.model.MuscleGroup
import io.github.lopezjavier.appfitness.domain.model.Phase

/**
 * Everything the next block's builder reads from the block that just ended (07 section 3 "Block builder" (e) and
 * "Block follow-up" (6)); built by `LoadNextBlockContextUseCase`.
 */
data class NextBlockContext(
    val previousPlanId: String,
    /** The ended block as stored: its sessions seed the builder. */
    val previousBlock: CoachBlock,
    /** Adherence of the ended block in percent; null without planned sessions (counts as under 80 %). */
    val adherencePct: Int?,
    /** Weekly sets per H1 group of the ended block (10 for a group the block does not hold). */
    val baseSets: Map<MuscleGroup, Int>,
    /** Main lifts that did not progress in the ended block ("puedes cambiarlo"). */
    val stalledLifts: Set<String>,
    /** The groups of the stalled lifts and of the stall diagnoses. */
    val stalledGroups: Set<MuscleGroup>,
    /** Groups with an open discomfort on one of their exercises. */
    val discomfortGroups: Set<MuscleGroup>,
    val nextPhase: Phase,
    val age: Int?,
) {
    val cut: Boolean get() = nextPhase == Phase.Cut

    /** The previous block's main lifts, to carry over. */
    val mainLiftIds: Set<String> get() = NextBlockSeed.mainLiftIds(previousBlock)

    /** Whether "Sugerencia: +2 series" is offered on [group] given its current [target]. */
    fun suggestion(group: MuscleGroup, target: Int): Boolean = NextBlockTargets.suggestionEligible(
        group,
        stalledGroups,
        adherencePct,
        discomfortGroups,
        cut,
        target,
        hardBlock = false,
    )
}
