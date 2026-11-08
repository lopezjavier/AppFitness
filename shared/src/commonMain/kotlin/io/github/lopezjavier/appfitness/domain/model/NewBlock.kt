package io.github.lopezjavier.appfitness.domain.model

/** What "Bloque nuevo" opens with: the review of the block so far (early or stored) and the goal comparison. */
data class NewBlockEntry(val review: BlockReview, val goal: GoalChange)

/**
 * Whether the saved training intention differs from the one the accepted plan was built on. The intentions are the
 * raw server values (e.g. `fat_loss`); [currentKcal] is today's calorie target.
 */
data class GoalChange(
    val changed: Boolean,
    val planIntention: String?,
    val savedIntention: String?,
    val currentKcal: Int?,
)

/** The deterministic proposal of a goal switch: the stored proposed plan and the calorie change it brings. */
data class GoalSwitchPreview(val planId: String, val currentKcal: Int?, val newKcal: Int?, val phase: Phase?)

/** Weeks started in the current calorie phase, counting consecutive blocks of the same phase. */
data class PhaseWeeks(val phase: Phase, val weeks: Int)
