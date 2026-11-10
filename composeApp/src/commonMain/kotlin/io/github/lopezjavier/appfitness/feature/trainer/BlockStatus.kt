package io.github.lopezjavier.appfitness.feature.trainer

import io.github.lopezjavier.appfitness.domain.model.BlockPause
import io.github.lopezjavier.appfitness.domain.model.PlanWeek
import io.github.lopezjavier.appfitness.domain.model.WeekPhase

/** The compact block summary on the Trainer root (US-120). */
sealed interface BlockStatus {
    data object None : BlockStatus

    data object Proposed : BlockStatus

    /**
     * [week] is the current week (null before the block starts or once it ended), [total] the number of weeks,
     * [adherence] the current week's adherence percent (null without a current week).
     */
    data class Saved(
        val week: Int?,
        val total: Int,
        val paused: Boolean,
        val reviewDue: Boolean,
        val adherence: Int?,
    ) : BlockStatus
}

/** Pure derivation of [BlockStatus] from the state the Trainer already holds. */
internal fun blockStatus(
    block: BlockSummary,
    weeks: List<PlanWeek>,
    pause: BlockPause?,
    reviewDue: Boolean,
): BlockStatus = when (block) {
    BlockSummary.None -> BlockStatus.None

    is BlockSummary.Proposed -> BlockStatus.Proposed

    is BlockSummary.Saved -> {
        val current = weeks.firstOrNull { it.phase == WeekPhase.Current }
        BlockStatus.Saved(
            week = current?.week,
            total = weeks.size,
            paused = pause?.paused == true,
            reviewDue = reviewDue,
            adherence = current?.adherencePct,
        )
    }
}
