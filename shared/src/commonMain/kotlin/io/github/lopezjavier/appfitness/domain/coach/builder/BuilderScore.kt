package io.github.lopezjavier.appfitness.domain.coach.builder

import io.github.lopezjavier.appfitness.domain.model.TrainingIntention
import kotlin.math.floor

/** The live score of the builder: an integer [percent] and whether every required slot is filled. */
data class BuilderScoreResult(val percent: Int, val complete: Boolean)

/**
 * Live score of the block builder (07 section 3 "Block builder" (c)): the folder review's weights rescaled without the
 * parts that have nothing to judge, counting only the slot's own group, with the prescribed sets as the target and no
 * 6.0 cap. Extras and cardio never count. 100 if and only if every required slot is filled.
 */
object BuilderScore {
    private const val W_VOLUME = 75.0
    private const val W_DAYS = 25.0
    private const val W_VOLUME_STRENGTH = 50.0
    private const val W_DAYS_STRENGTH = 20.0
    private const val PERCENT = 100.0
    private const val HALF = 0.5

    fun score(days: List<BuilderDay>, intention: TrainingIntention?): BuilderScoreResult {
        val strength = intention == TrainingIntention.Strength
        val required = days.map { it.slots }
        val pV = SplitCatalog.ALL_GROUPS.map { group ->
            val slots = required.flatten().filter { it.slot.group == group }
            val target = slots.sumOf { it.slot.sets }
            val filled = slots.filter { it.pick != null }.sumOf { it.slot.sets }
            if (target == 0) 1.0 else minOf(filled.toDouble() / target, 1.0)
        }.average()
        val pD = if (days.isEmpty()) 0.0 else required.count { d -> d.any { it.pick != null } }.toDouble() / days.size
        val wV = if (strength) W_VOLUME_STRENGTH else W_VOLUME
        val wD = if (strength) W_DAYS_STRENGTH else W_DAYS
        val percent = floor(PERCENT * (wV * pV + wD * pD) / (wV + wD) + HALF).toInt()
        return BuilderScoreResult(percent, required.flatten().all { it.pick != null })
    }
}
